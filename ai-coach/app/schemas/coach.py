"""코치 요청/응답 스키마."""

from enum import Enum

from pydantic import BaseModel, Field


class Stage(str, Enum):
    """야구 비유 단계. Spring 의 PlanStage 와 값이 같다."""

    BENCH = "BENCH"
    FIRST = "FIRST"
    SECOND = "SECOND"
    THIRD = "THIRD"
    HOME = "HOME"


class ResponseType(str, Enum):
    ANSWER = "ANSWER"
    CLARIFICATION = "CLARIFICATION"
    NO_EVIDENCE = "NO_EVIDENCE"


class Source(BaseModel):
    """근거 출처. LLM 이 아니라 retriever 가 돌려준 문서 메타데이터로만 채운다."""

    title: str
    source: str
    source_url: str | None = None
    snippet: str


class AskRequest(BaseModel):
    question: str = Field(..., min_length=1, description="사용자 질문")
    stage: Stage = Field(..., description="현재 단계. 검색 필터·페르소나에 쓰인다.")
    context: dict | None = Field(default=None, description="선택. 프론트가 넘기는 사용자 요약(진단·정책 등).")
    conversation_id: str = Field(
        default="main",
        min_length=1,
        max_length=64,
        pattern=r"^[A-Za-z0-9_-]+$",
        description="단기 대화 문맥 식별자",
    )


class AskResponse(BaseModel):
    answer: str
    stage: Stage
    sources: list[Source]
    response_type: ResponseType = ResponseType.ANSWER
    summary: str = ""
    reasons: list[str] = Field(default_factory=list)
    next_actions: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    follow_up_question: str | None = None
    conversation_id: str = "main"
