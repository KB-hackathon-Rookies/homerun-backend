package com.homerun.domain.diagnosis.type;

public enum DiagnosisWarning {
    TARGET_MOVE_DATE_MISSING,
    MONTHLY_DEBT_PAYMENT_UNCONFIRMED,
    OPEN_BANKING_INCOME_UNCONFIRMED,
    /** 중개보수·인지세·보증료·이사비를 사용자가 주지 않아 기준 수치로 계산했다. */
    ANCILLARY_COST_ESTIMATED,
    /** 보증금이 중개보수 요율표 구간을 벗어나 중개보수를 산출하지 못했다. 초기 필요자금이 실제보다 작다. */
    BROKERAGE_FEE_UNKNOWN,
    /** 월 생활비를 모른다. 월 여유자금과 저축 가능액이 실제보다 크게 나온다. */
    MONTHLY_LIVING_EXPENSE_MISSING,
    /** 입주 후 예비비를 모른다. 초기 필요자금이 실제보다 작다. */
    EMERGENCY_RESERVE_MISSING
}
