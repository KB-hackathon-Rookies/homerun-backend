"""예상 질문 세트로 실제 RAG 파이프라인을 돌려 보는 실답변 eval 러너.

코퍼스 커버리지 테스트(오프라인)와 달리, 이건 진짜 OpenAI 임베딩·검색·답변까지 돌린다.
그래서 아래가 모두 필요하다:

    1) 의존성 설치      pip install -r requirements.txt
    2) OPENAI_API_KEY   .env 또는 환경변수
    3) 인제스트된 코퍼스  python -m scripts.ingest --path data/corpus

사용법:

    python -m scripts.eval_coach

각 질문마다 두 가지를 본다.
    retrieval  검색된 근거 청크(top_k)에 기대 핵심어가 들어왔는가
    grounded   답변이 근거(sources)를 달고 나왔고, "못 찾음" 폴백이 아닌가

CI 에서는 OPENAI_API_KEY 시크릿을 넣고 별도 잡으로 돌린다. 오프라인 유닛테스트 스위트에는
포함되지 않는다(외부 호출·과금이 있으므로). 근거 없는 질문이 하나라도 있으면 종료코드 1.
"""

import sys

from app.core.config import settings
from app.schemas.coach import Stage
from app.services import vectorstore
from app.services.embeddings import embed_query
from app.services.rag import answer
from tests.coach_eval_questions import EVAL_QUESTIONS

# 코퍼스가 항상 후보에 포함하는 공통(ALL) 문서 덕분에, 단계 무관 질문은 아무 단계로 물어도 된다.
_DEFAULT_STAGE = Stage.FIRST


def _stage_of(raw: str) -> Stage:
    try:
        return Stage(raw)
    except ValueError:
        return _DEFAULT_STAGE


def _preconditions_ok() -> bool:
    if not settings.openai_api_key:
        print("OPENAI_API_KEY 가 없습니다. .env 또는 환경변수에 설정하세요.", file=sys.stderr)
        return False
    try:
        count = vectorstore.count()
    except Exception as exc:  # noqa: BLE001 - 사전 점검이라 원인과 무관하게 안내만 한다.
        print(f"ChromaDB 를 열 수 없습니다: {exc}", file=sys.stderr)
        return False
    if count == 0:
        print("코퍼스가 인제스트되지 않았습니다. `python -m scripts.ingest --path data/corpus` 먼저 실행하세요.", file=sys.stderr)
        return False
    return True


def main() -> int:
    if not _preconditions_ok():
        return 2

    retrieval_hits = 0
    grounded_hits = 0
    failures: list[str] = []

    for question in EVAL_QUESTIONS:
        stage = _stage_of(question.stage)

        hits = vectorstore.search(embed_query(question.question), stage.value, settings.top_k)
        retrieval_ok = any(question.keyword in hit.snippet for hit in hits)

        response = answer(question.question, stage, None)
        grounded = bool(response.sources) and "찾지 못" not in response.answer

        retrieval_hits += retrieval_ok
        grounded_hits += grounded
        if not (retrieval_ok and grounded):
            failures.append(question.question)

        print(f"[{'PASS' if retrieval_ok and grounded else 'FAIL'}] ({question.stage}) {question.question}")
        print(f"    retrieval={'O' if retrieval_ok else 'X'}  grounded={'O' if grounded else 'X'}"
              f"  sources={len(response.sources)}")
        print(f"    답변: {response.answer.strip()[:120]}")

    total = len(EVAL_QUESTIONS)
    print(f"\n요약: 검색 {retrieval_hits}/{total} · 근거답변 {grounded_hits}/{total}")
    if failures:
        print("실패 질문:")
        for question in failures:
            print(f"  - {question}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
