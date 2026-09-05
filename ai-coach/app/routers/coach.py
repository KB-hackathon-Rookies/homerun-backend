"""코치 엔드포인트."""

from fastapi import APIRouter, Depends

from app.core.security import get_current_member_id
from app.schemas.coach import AskRequest, AskResponse
from app.services import rag

router = APIRouter(prefix="/coach", tags=["coach"])


@router.post("/ask", response_model=AskResponse)
def ask(request: AskRequest, member_id: int = Depends(get_current_member_id)) -> AskResponse:
    """질문 + 현재 단계로 근거 붙은 답변을 돌려준다. 인증(Spring JWT)만 확인하고 본문 데이터는 신뢰한다."""
    return rag.answer(request.question, request.stage, request.context)
