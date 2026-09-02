package com.homerun.domain.contract.type;

/**
 * 계약 진행 단계(PRP-02-04).
 *
 * <p>선언 순서가 진행 순서다. 확정일자를 잔금 뒤로 미루는 사람이 많지만, 계약서를 쓴 직후에
 * 받아 두면 전입신고와 별개로 우선변제권 순위를 먼저 잡을 수 있다.
 */
public enum ContractStep {
    CONTRACT_SIGNED("계약 체결"),
    DOWN_PAYMENT("계약금 납부"),
    CONFIRMED_DATE("확정일자"),
    LOAN_APPLIED("대출 신청"),
    BALANCE_PAID("잔금 지급");

    private final String label;

    ContractStep(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
