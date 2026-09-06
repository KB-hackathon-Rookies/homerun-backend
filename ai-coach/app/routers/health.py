"""헬스체크. 인증 불필요."""

from fastapi import APIRouter

from app.core.config import settings
from app.services import vectorstore

router = APIRouter(tags=["health"])


@router.get("/health")
def health() -> dict:
    try:
        doc_count = vectorstore.count()
        chroma_ok = True
    except Exception:  # noqa: BLE001 - 헬스체크는 원인과 무관하게 down 만 보고한다.
        doc_count = 0
        chroma_ok = False
    return {
        "status": "ok",
        "chatModel": settings.openai_chat_model,
        "embeddingModel": settings.openai_embedding_model,
        "chromaOk": chroma_ok,
        "documentCount": doc_count,
    }
