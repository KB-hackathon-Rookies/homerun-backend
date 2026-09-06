package com.homerun.domain.property.type;

/**
 * 거절 사유 분류(BR-24, FR-P8-01). 무엇 때문에 막혔는가에 따라 대안이 완전히 다르다.
 * DB CHECK 제약(ck_bank_consultation_rejection_category)과 값이 같아야 한다.
 */
public enum RejectionCategory {
    /** 사람 문제(소득·신용·무주택). 은행을 바꿔도 소용없다 — 조건 조정·상품 변경. */
    SUBJECT_ISSUE,
    /** 집 문제. 위반건축물·근생은 매물 변경만, 근저당 과다·시세 불명은 보증기관 변경 여지. */
    PROPERTY_ISSUE,
    /** 보증기관 거절. 같은 은행에서 담보를 바꾼다(HUG→SGI·HF, SGI→정책형, HF→SGI). */
    GUARANTEE_ISSUE,
    /** 서류 미비. 보완 후 재신청. */
    DOCUMENT_ISSUE,
    /** 임대인 문제. 특약 발동 또는 설득. */
    LANDLORD_ISSUE
}
