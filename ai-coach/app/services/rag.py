"""stage-aware RAG. 답변은 LLM 이, 근거(sources)는 retriever 결과가 만든다."""

from functools import lru_cache

from openai import OpenAI

from app.core.config import settings
from app.schemas.coach import AskResponse, Source, Stage
from app.services import vectorstore
from app.services.embeddings import embed_query

# 단계별 코치 페르소나. 같은 질문이라도 사용자가 선 단계에 맞춰 답의 결을 바꾼다.
_STAGE_PERSONA: dict[Stage, str] = {
    Stage.BENCH: "사용자는 이제 막 독립을 준비하는 단계다. 용어를 쉽게 풀고 첫 걸음을 안내한다.",
    Stage.FIRST: "사용자는 자금·대출 진단 단계다. 자격과 한도, 다음 확인 사항을 구체적으로 짚는다.",
    Stage.SECOND: "사용자는 정책 선택·매물 검증 단계다. 비교 기준과 확정에 필요한 근거를 제시한다.",
    Stage.THIRD: "사용자는 계약·실행 단계다. 되돌릴 수 없는 실수(순서 함정)를 특히 경고한다.",
    Stage.HOME: "사용자는 입주 후 정착 단계다. 현금흐름·상환·정책 유지 관점으로 조언한다.",
}

_SYSTEM_BASE = (
    "너는 사회초년생의 첫 독립을 돕는 금융·주거 코치 '홈런 코치'다. "
    "반드시 아래 제공된 근거 문서 안의 내용으로만 답한다. 근거가 부족하면 모른다고 말하고 "
    "일반론으로 지어내지 않는다. 한국어로, 실행 가능한 다음 단계를 중심으로 간결하게 답한다."
)


@lru_cache
def _client() -> OpenAI:
    return OpenAI(api_key=settings.openai_api_key)


def answer(question: str, stage: Stage, context: dict | None) -> AskResponse:
    query_embedding = embed_query(question)
    hits = vectorstore.search(query_embedding, stage.value, settings.top_k)

    answer_text = _generate(question, stage, hits, context)

    # 근거는 LLM 출력이 아니라 retriever 히트에서 직접 만든다(출처 환각 방지).
    sources = [Source(title=hit.title, source=hit.source, snippet=_shorten(hit.snippet)) for hit in hits]
    return AskResponse(answer=answer_text, stage=stage, sources=sources)


def _generate(question: str, stage: Stage, hits: list, context: dict | None) -> str:
    if not hits:
        return "관련 근거 문서를 찾지 못했어요. 질문을 조금 더 구체적으로 적어 주시면 도와드릴게요."

    context_block = "\n\n".join(
        f"[근거 {index}] 제목: {hit.title} (출처: {hit.source})\n{hit.snippet}"
        for index, hit in enumerate(hits, start=1)
    )
    user_context = f"\n\n[사용자 상황]\n{context}" if context else ""
    system_prompt = f"{_SYSTEM_BASE}\n\n[현재 단계 안내] {_STAGE_PERSONA.get(stage, '')}"
    user_prompt = f"[근거 문서]\n{context_block}{user_context}\n\n[질문]\n{question}"

    completion = _client().chat.completions.create(
        model=settings.openai_chat_model,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        temperature=0.2,
    )
    return completion.choices[0].message.content or ""


def _shorten(text: str, limit: int = 200) -> str:
    text = text.strip().replace("\n", " ")
    return text if len(text) <= limit else text[:limit] + "…"
