"""ChromaDB(임베디드 persistent) 접근. stage 필터는 현재 stage + 'ALL' 로 건다."""

from dataclasses import dataclass
from functools import lru_cache

import chromadb

from app.core.config import settings

# 코퍼스 문서 중 단계 무관 공통 문서에 붙이는 stage 값.
STAGE_ALL = "ALL"


@dataclass
class Hit:
    """검색 결과 한 건. sources 는 오직 이 값으로만 구성한다."""

    title: str
    source: str
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


def count() -> int:
    return get_collection().count()


def search(query_embedding: list[float], stage: str, top_k: int) -> list[Hit]:
    """현재 stage 전용 문서 + 공통(ALL) 문서를 함께 후보로 검색한다."""
    result = get_collection().query(
        query_embeddings=[query_embedding],
        n_results=top_k,
        where={"stage": {"$in": [stage, STAGE_ALL]}},
        include=["documents", "metadatas", "distances"],
    )
    documents = (result.get("documents") or [[]])[0]
    metadatas = (result.get("metadatas") or [[]])[0]
    distances = (result.get("distances") or [[]])[0]

    hits: list[Hit] = []
    for document, metadata, distance in zip(documents, metadatas, distances):
        metadata = metadata or {}
        hits.append(
            Hit(
                title=str(metadata.get("title", "제목 없음")),
                source=str(metadata.get("source", "출처 미상")),
                snippet=document or "",
                distance=float(distance),
            )
        )
    return hits
