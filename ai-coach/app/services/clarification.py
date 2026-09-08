"""개인화 판단에 꼭 필요한 값이 없을 때 성급한 결론 대신 되묻는다."""

from app.schemas.coach import Stage

_PERSONAL_MARKERS = ("내 ", "나는", "제가", "저는", "가능할", "가능해", "한도")
_LOAN_MARKERS = ("대출", "버팀목", "이자지원")
_INCOME_KEYS = {"annualIncome", "annual_income", "income", "연소득"}
_DEPOSIT_KEYS = {"deposit", "leaseDeposit", "lease_deposit", "보증금"}
_BALANCE_DATE_KEYS = {"balanceDate", "balance_date", "잔금일"}


def missing_information(question: str, stage: Stage, context: dict | None) -> str | None:
    personal_loan_question = stage == Stage.FIRST and any(
        marker in question for marker in _PERSONAL_MARKERS
    ) and any(
        marker in question for marker in _LOAN_MARKERS
    )
    if personal_loan_question and not _has_value(context, _INCOME_KEYS):
        return "정확히 판단하려면 세전 연소득이 얼마인지 알려주세요."
    if personal_loan_question and "한도" in question and not _has_value(context, _DEPOSIT_KEYS):
        return "예상 한도를 계산하려면 계약하려는 집의 보증금이 얼마인지 알려주세요."
    if stage == Stage.THIRD and any(marker in question for marker in ("일정", "언제", "잔금")):
        if not _has_value(context, _BALANCE_DATE_KEYS):
            return "계약 일정을 계산하려면 잔금 예정일을 알려주세요."
    return None


def _has_value(context: dict | None, keys: set[str]) -> bool:
    if not context:
        return False
    return any(context.get(key) not in (None, "") for key in keys)
