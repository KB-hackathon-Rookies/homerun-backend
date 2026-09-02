package com.homerun.domain.rent;

/** 월세 관련 정책 대출 상품. */
public enum RentLoanProduct {
    /** 청년전용 보증부월세대출. 보증금분과 월세분을 함께 빌린다. */
    DEPOSIT_BACKED("청년전용 보증부월세대출"),
    /** 주거안정 월세대출. 월세자금만 빌린다. */
    HOUSING_STABILITY("주거안정 월세대출");

    private final String label;

    RentLoanProduct(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
