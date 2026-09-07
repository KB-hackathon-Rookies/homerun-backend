"""홈런 AI 코치 예상 질문 세트(단계별).

사용자가 실제로 물을 법한 질문을 코퍼스의 근거 ``topic`` 에 매핑한다. 코퍼스 커버리지
테스트(``test_coach_corpus_coverage``)가 "이 질문에 답할 근거 문서가 실제로 있는가"를
검증하는 데 쓰고, OpenAI 키가 있을 때 도는 실답변 eval 러너(``eval_coach_answers``)도 같은
세트를 재사용한다.

새 코퍼스 문서를 추가하면 여기에 대응하는 질문도 추가한다(커버리지 테스트가 강제한다).
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class EvalQuestion:
    """예상 질문 한 건."""

    question: str
    stage: str  # BENCH/FIRST/SECOND/THIRD/HOME/ALL
    topic: str  # 답변 근거가 되는 코퍼스 topic
    keyword: str  # 근거 문서 본문에 반드시 있어야 하는 핵심어


EVAL_QUESTIONS: list[EvalQuestion] = [
    # 공통(ALL)
    EvalQuestion("이 서비스는 전체적으로 어떤 단계로 진행돼?", "ALL", "service_flow", "단계"),
    EvalQuestion("코치가 답변할 때 지키는 원칙이나 한계가 있어?", "ALL", "safety_boundary", "한계"),
    # 1루
    EvalQuestion("내 소득이면 청년 버팀목을 받을 수 있을까?", "FIRST", "loan_eligibility", "청년"),
    EvalQuestion("일반 버팀목이랑 서울시 이자지원은 뭐가 달라?", "FIRST", "loan_comparison", "서울"),
    EvalQuestion("진단 결과에서 최대 대출 가능액은 어떻게 봐?", "FIRST", "diagnosis_result", "대출"),
    EvalQuestion("입력하다가 중간에 나가면 저장돼?", "FIRST", "diagnosis_input", "저장"),
    # 2루
    EvalQuestion("이 매물이 반환보증 가입되는지 어떻게 확인해?", "SECOND", "return_guarantee", "126"),
    EvalQuestion("은행 사전상담 때 뭘 물어봐야 해?", "SECOND", "bank_consultation", "상담"),
    EvalQuestion("다가구는 왜 위험하다고 해?", "SECOND", "property_risk", "다가구"),
    EvalQuestion("매물을 여러 개 등록해서 비교할 수 있어?", "SECOND", "property_workflow", "비교"),
    EvalQuestion("전세사기는 어떤 유형이 있고 어떻게 막아?", "SECOND", "fraud_types", "깡통전세"),
    EvalQuestion("계약 전에 임대인이 어떤 사람인지 확인할 수 있어?", "SECOND", "landlord_check", "임대인 정보 조회"),
    # 3루
    EvalQuestion("계약할 때 꼭 넣어야 하는 특약이 뭐야?", "THIRD", "contract_safety", "특약"),
    EvalQuestion("잔금일을 정하면 언제 뭘 해야 하는지 알려줘", "THIRD", "contract_schedule", "잔금"),
    EvalQuestion("대출 신청하려면 어떤 서류를 떼야 해?", "THIRD", "document_guide", "서류"),
    EvalQuestion("대항력이랑 확정일자, 우선변제권이 뭐가 달라?", "THIRD", "opposing_power", "우선변제권"),
    # 홈
    EvalQuestion("입주하고 나서 반환보증이랑 보증료 지원은 어떻게 챙겨?", "HOME", "guarantee_support", "보증료"),
    EvalQuestion("매달 나가는 이자랑 연체는 어떻게 관리해?", "HOME", "cashflow", "연체"),
    EvalQuestion("2년 뒤에 계약을 갱신할지 나갈지 어떻게 정해?", "HOME", "renewal_moveout", "갱신"),
    EvalQuestion("연말정산 때 전세대출 소득공제 받을 수 있어?", "HOME", "tax_rate_review", "소득공제"),
    EvalQuestion("보증금을 못 돌려받으면 어디에 도움을 요청해?", "HOME", "incident_help", "전세피해확인서"),
]
