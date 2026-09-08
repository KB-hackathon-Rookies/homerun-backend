"""ChromaDB(임베디드 persistent) 접근. stage 필터는 현재 stage + 'ALL' 로 건다."""

from dataclasses import dataclass
from datetime import date
from functools import lru_cache
import re

import chromadb

from app.core.config import settings

# 코퍼스 문서 중 단계 무관 공통 문서에 붙이는 stage 값.
STAGE_ALL = "ALL"


@dataclass
class Hit:
    """검색 결과 한 건. sources 는 오직 이 값으로만 구성한다."""

    title: str
    source: str
    source_url: str | None
    snippet: str
    distance: float


@lru_cache
def _client() -> chromadb.ClientAPI:
    return chromadb.PersistentClient(path=settings.chroma_dir)


def get_collection():
    return _client().get_or_create_collection(
        name=settings.chroma_collection,
        metadata={"hnsw:space": "cosine"},
    )


def upsert(ids: list[str], embeddings: list[list[float]], documents: list[str], metadatas: list[dict]) -> None:
    get_collection().upsert(ids=ids, embeddings=embeddings, documents=documents, metadatas=metadatas)


def delete_source(source_path: str) -> None:
    """재인제스트 전에 해당 파일의 이전 청크를 지운다.

    문서가 짧아졌을 때 과거의 뒤쪽 청크가 남아 검색되는 일을 막는다.
    """
    get_collection().delete(where={"source_path": source_path})


def reset_collection() -> None:
    """전체 코퍼스를 다시 만들 때 기존 컬렉션을 원자적으로 새로 연다."""
    client = _client()
    collections = client.list_collections()
    collection_names = {
        collection if isinstance(collection, str) else collection.name
        for collection in collections
    }
    if settings.chroma_collection in collection_names:
        client.delete_collection(settings.chroma_collection)
    get_collection()


def count() -> int:
    return get_collection().count()


def search(query_embedding: list[float], stage: str, top_k: int) -> list[Hit]:
    """하위 호환용 dense 검색 진입점."""
    return hybrid_search(query_embedding, "", stage, top_k)


def hybrid_search(query_embedding: list[float], query_text: str, stage: str, top_k: int) -> list[Hit]:
    """dense + 키워드 RRF 검색 후 날짜·거리·문서 중복을 정리한다."""
    where = {"stage": {"$in": [stage, STAGE_ALL]}}
    candidate_count = max(top_k, top_k * settings.retrieval_candidate_multiplier)
    result = get_collection().query(
        query_embeddings=[query_embedding],
        n_results=candidate_count,
        where=where,
        include=["documents", "metadatas", "distances"],
    )
    dense_ids = (result.get("ids") or [[]])[0]
    documents = (result.get("documents") or [[]])[0]
    metadatas = (result.get("metadatas") or [[]])[0]
    distances = (result.get("distances") or [[]])[0]

    candidates: dict[str, dict] = {}
    for rank, (doc_id, document, metadata, distance) in enumerate(
        zip(dense_ids, documents, metadatas, distances), start=1
    ):
        metadata = metadata or {}
        if not _is_effective(metadata):
            continue
        candidates[str(doc_id)] = {
            "hit": _hit(document, metadata, float(distance)),
            "metadata": metadata,
            "score": 1.0 / (60 + rank),
            "dense_ok": float(distance) <= settings.retrieval_max_distance,
            "lexical_ok": False,
        }

    if query_text.strip():
        lexical_result = get_collection().get(where=where, include=["documents", "metadatas"])
        lexical_rows: list[tuple[float, str, str, dict]] = []
        for doc_id, document, metadata in zip(
            lexical_result.get("ids") or [],
            lexical_result.get("documents") or [],
            lexical_result.get("metadatas") or [],
        ):
            metadata = metadata or {}
            score = _keyword_score(query_text, document or "")
            if score > 0 and _is_effective(metadata):
                lexical_rows.append((score, str(doc_id), document or "", metadata))
        lexical_rows.sort(key=lambda row: row[0], reverse=True)

        for rank, (_, doc_id, document, metadata) in enumerate(lexical_rows[:candidate_count], start=1):
            candidate = candidates.setdefault(
                doc_id,
                {
                    "hit": _hit(document, metadata, 1.0),
                    "metadata": metadata,
                    "score": 0.0,
                    "dense_ok": False,
                    "lexical_ok": True,
                },
            )
            candidate["score"] += 1.0 / (60 + rank)
            candidate["lexical_ok"] = True

    ranked = sorted(candidates.values(), key=lambda candidate: candidate["score"], reverse=True)
    hits: list[Hit] = []
    seen_sources: set[str] = set()
    for candidate in ranked:
        if not (candidate["dense_ok"] or candidate["lexical_ok"]):
            continue
        metadata = candidate["metadata"]
        source_key = str(metadata.get("source_path") or f"{metadata.get('source')}:{metadata.get('title')}")
        if source_key in seen_sources:
            continue
        seen_sources.add(source_key)
        hits.append(candidate["hit"])
        if len(hits) == top_k:
            break
    return hits


def _hit(document: str, metadata: dict, distance: float) -> Hit:
    return Hit(
        title=str(metadata.get("title", "제목 없음")),
        source=str(metadata.get("source", "출처 미상")),
        source_url=str(metadata["source_url"]) if metadata.get("source_url") else None,
        snippet=document or "",
        distance=distance,
    )


def _keyword_score(query: str, document: str) -> float:
    aliases = {
        "버팀목": ("전세자금대출", "주택도시기금"),
        "보증": ("반환보증", "HUG", "HF"),
        "등기부": ("등기사항전부증명서", "권리관계"),
        "연장": ("갱신", "재계약"),
    }
    normalized_document = document.lower()
    tokens = _tokens(query)
    expanded = set(tokens)
    for token in tokens:
        expanded.update(alias.lower() for alias in aliases.get(token, ()))
    return float(sum(1 for token in expanded if token in normalized_document))


def _tokens(text: str) -> set[str]:
    stopwords = {"어떻게", "알려줘", "뭐야", "무엇", "있어", "하는", "해야", "관련", "현재", "질문"}
    return {
        token.lower()
        for token in re.findall(r"[0-9A-Za-z가-힣]+", text)
        if len(token) >= 2 and token.lower() not in stopwords
    }


def _is_effective(metadata: dict) -> bool:
    today = date.today().isoformat()
    effective_from = str(metadata.get("effective_from", ""))
    effective_to = str(metadata.get("effective_to", ""))
    return (not effective_from or effective_from <= today) and (not effective_to or today <= effective_to)
