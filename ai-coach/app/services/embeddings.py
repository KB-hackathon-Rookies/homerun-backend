"""OpenAI 임베딩 래퍼."""

from functools import lru_cache

from openai import OpenAI

from app.core.config import settings


@lru_cache
def _client() -> OpenAI:
    return OpenAI(api_key=settings.openai_api_key)


def embed_texts(texts: list[str]) -> list[list[float]]:
    """여러 텍스트를 한 번에 임베딩한다."""
    if not texts:
        return []
    response = _client().embeddings.create(model=settings.openai_embedding_model, input=texts)
    return [item.embedding for item in response.data]


def embed_query(text: str) -> list[float]:
    return embed_texts([text])[0]
