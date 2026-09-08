package com.homerun.domain.policy.type;

import java.util.Map;

/**
 * 대출 부적합 사유 분류(POL-03-10). "사용자 조건, 주택 조건, 한도 중 어느 것 때문에 불가한지" —
 * 사유별로 안내할 대안이 다르다.
 *
 * <p>다른 {@code type/} enum과 달리 DB CHECK 제약과 짝이 아니다. {@code rejection_reason}
 * 테이블에는 컬럼이 없다 — {@code reason_code} → 카테고리는 순수 함수라 {@link #from} 으로만
 * 계산한다. 판정 시점(JeonsePolicyVerdictService)과 사후 조회(ALT-01-01 원인 분석) 양쪽이
 * 같은 분류를 써야 해서 여기 하나로 모았다.
 */
public enum RejectionReasonCategory {
    /** 소득·자산·연령·세대주 여부 등 사람에 관한 조건. */
    USER,
    /** 위반건축물·다가구·면적·공시가 대비 보증금 비율 등 매물에 관한 조건. */
    HOUSE,
    /** 상품이 처리 가능한 금액 자체의 상한(임차보증금 상한 등). */
    LIMIT;

    /** FAIL 조건 코드 → 분류(#108). 맵에 없는 조건(RULE_NOT_ACTIVE 등 코드가 아닌 것)은
     * null — 없는 카테고리를 지어내지 않는다. */
    private static final Map<String, RejectionReasonCategory> BY_REASON_CODE = Map.ofEntries(
            Map.entry("HOUSEHOLD_HOMELESS", USER),
            Map.entry("HOUSEHOLDER_STATUS", USER),
            Map.entry("NO_DUPLICATE_LOAN", USER),
            Map.entry("AGE_UPPER_BOUND", USER),
            Map.entry("INCOME_CAP", USER),
            Map.entry("NET_ASSET_CAP", USER),
            Map.entry("REQUIRES_HF_JEONSE_LOAN", USER),
            Map.entry("INCOME_CAP_FEE_SUPPORT", USER),
            Map.entry("NOT_VIOLATION_BUILDING", HOUSE),
            Map.entry("NOT_MULTI_HOUSEHOLD", HOUSE),
            Map.entry("RESIDENTIAL_USE", HOUSE),
            Map.entry("AREA_CAP", HOUSE),
            Map.entry("PRICE_RATIO_126", HOUSE),
            Map.entry("DEPOSIT_CAP", LIMIT));

    public static RejectionReasonCategory from(String reasonCode) {
        return BY_REASON_CODE.get(reasonCode);
    }
}
