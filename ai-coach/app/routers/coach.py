"""코치 엔드포인트."""

from fastapi import APIRouter, Depends, Path, status

from app.core.security import get_current_member_id
from app.schemas.coach import AskRequest, AskResponse
from app.services import rag
from app.services.conversation import ConversationStore, get_conversation_store

router = APIRouter(prefix="/coach", tags=["coach"])


@router.post("/ask", response_model=AskResponse)
def ask(
    request: AskRequest,
    member_id: int = Depends(get_current_member_id),
    conversations: ConversationStore = Depends(get_conversation_store),
) -> AskResponse:
    """질문 + 현재 단계로 근거 붙은 답변을 돌려준다. 인증(Spring JWT)만 확인하고 본문 데이터는 신뢰한다."""
    history = conversations.load(member_id, request.conversation_id)
    response = rag.answer(
        request.question,
        request.stage,
        request.context,
        history=history,
        conversation_id=request.conversation_id,
    )
    conversations.append(member_id, request.conversation_id, request.question, response.answer)
    return response


@router.delete("/conversations/{conversation_id}", status_code=status.HTTP_204_NO_CONTENT)
def clear_conversation(
    conversation_id: str = Path(..., min_length=1, max_length=64, pattern=r"^[A-Za-z0-9_-]+$"),
    member_id: int = Depends(get_current_member_id),
    conversations: ConversationStore = Depends(get_conversation_store),
) -> None:
    """현재 회원의 단기 대화 기록을 즉시 삭제한다."""
    conversations.clear(member_id, conversation_id)
