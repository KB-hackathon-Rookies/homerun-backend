package com.homerun.domain.rent.type;

/** 월세 관련 지원금 종류. 상호배타 관계가 있어 동시에 다 받을 수는 없다. */
public enum RentSupportType {
    /** 주거급여. 소득인정액이 기준중위소득 48% 이하일 때. */
    HOUSING_BENEFIT("주거급여"),
    /** 국토부 청년월세 특별지원. 월 최대 20만원 × 최장 24개월. */
    YOUTH_RENT_NATIONAL("청년월세 특별지원"),
    /** 지자체 청년 월세지원. 서울시 등. */
    YOUTH_RENT_LOCAL("지자체 청년월세지원");

    private final String label;

    RentSupportType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
