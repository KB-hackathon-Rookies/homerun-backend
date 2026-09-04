package com.homerun.domain.loan.type;

public enum JeonseLoanProduct {
    YOUTH_BEOTIMMOK("청년 버팀목 전세자금대출"),
    GENERAL_BEOTIMMOK("버팀목 전세자금대출"),
    BANK_JEONSE_LOAN("은행 전세자금대출");

    private final String displayName;

    JeonseLoanProduct(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
