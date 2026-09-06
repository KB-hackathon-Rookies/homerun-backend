package com.homerun.domain.settlement.type;

/**
 * 보증료 지원 대상 구분(BR-31). 지원율이 갈린다 — 청년·신혼은 전액, 청년 외는 90%.
 *
 * <p>어느 구분에 드는지(청년 연령 지자체 조례 등)는 자격 판정의 몫이라 여기서 정하지 않는다.
 * 금액 계산에는 구분만 받는다.
 */
public enum GuaranteeFeeSupportCategory {
    /** 청년(연소득 5,000만 이하). 전액. */
    YOUTH,
    /** 청년·신혼 외 무주택자(연소득 6,000만 이하). 90%. */
    GENERAL,
    /** 신혼부부(부부합산 7,500만 이하). 전액. */
    NEWLYWED
}
