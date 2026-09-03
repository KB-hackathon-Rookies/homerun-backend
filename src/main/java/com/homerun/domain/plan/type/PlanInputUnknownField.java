package com.homerun.domain.plan.type;

public enum PlanInputUnknownField {
    // 주거 비용
    HOPE_DEPOSIT, // 희망 보증금
    CURRENT_DEPOSIT, // 기존 보증금
    MONTHLY_RENT, // 희망 월세
    MAINTENANCE_FEE, // 관리비
    MAX_MONTHLY_BURDEN, // 감당 가능한 월 부담 상한

    // 주거 조건
    REGION_ID, // 희망 지역
    AREA_M2, // 희망 전용면적
    HOUSE_TYPE, // 주택 유형

    // 가구 조건
    IS_HOMELESS, // 무주택 여부
    HOUSEHOLDER_STATUS, // 세대주 상태
    MARITAL_STATUS, // 혼인 상태

    // 직업 조건
    EMPLOYMENT_TYPE, // 고용 형태
    EMPLOYMENT_MONTHS, // 재직 개월
    COMPANY_SIZE // 기업 규모
}
