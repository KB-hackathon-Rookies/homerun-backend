package com.homerun.domain.policy.type;

/**
 * 대출 부적합 사유 분류(POL-03-10). "사용자 조건, 주택 조건, 한도 중 어느 것 때문에 불가한지" —
 * 사유별로 안내할 대안이 다르다.
 *
 * <p>다른 {@code type/} enum과 달리 DB CHECK 제약과 짝이 아니다. {@code rejection_reason}
 * 테이블에는 컬럼이 없다 — {@code reason_code} → 카테고리는 순수 함수라 응답을 만들 때만
 * 계산한다({@link com.homerun.domain.policy.service.JeonsePolicyVerdictService}).
 */
public enum RejectionReasonCategory {
    /** 소득·자산·연령·세대주 여부 등 사람에 관한 조건. */
    USER,
    /** 위반건축물·다가구·면적·공시가 대비 보증금 비율 등 매물에 관한 조건. */
    HOUSE,
    /** 상품이 처리 가능한 금액 자체의 상한(임차보증금 상한 등). */
    LIMIT
}
