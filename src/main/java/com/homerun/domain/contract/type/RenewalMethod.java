package com.homerun.domain.contract.type;

/** 계약 갱신 방법(FR-H9-02). */
public enum RenewalMethod {
    /** 계약갱신청구권. 1회 행사, 임대료 인상 5% 상한. */
    CLAIM,
    /** 묵시적 갱신. 기존 조건 그대로 연장. */
    IMPLIED,
    /** 합의 갱신. 양측 합의로 조건 변경, 인상률 제한 없음. */
    AGREED
}
