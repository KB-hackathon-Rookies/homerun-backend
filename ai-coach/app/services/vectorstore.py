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
                source_url=str(metadata["source_url"]) if metadata.get("source_url") else None,
                snippet=document or "",
                distance=float(distance),
            )
        )
    return hits
