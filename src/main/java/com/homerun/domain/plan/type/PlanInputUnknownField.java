package com.homerun.domain.plan.type;

/**
 * 모름으로 남길 수 있는 입력 항목(COM-05-04).
 *
 * <p>모름은 불가가 아니라 추가확인이다. 어떤 항목을 모름으로 둘 수 있는지는 여기 한 곳에서만
 * 정한다. 이전에는 서비스 안의 문자열 화이트리스트였는데, 항목을 늘릴 때 빠뜨리기 쉬웠다.
 */
public enum PlanInputUnknownField {
    HOPE_DEPOSIT,
    CURRENT_DEPOSIT,
    MONTHLY_RENT,
    MAINTENANCE_FEE,
    MAX_MONTHLY_BURDEN,
    REGION_ID,
    AREA_M2,
    HOUSE_TYPE,
    IS_HOMELESS,
    HOUSEHOLDER_STATUS,
    MARITAL_STATUS,
    EMPLOYMENT_TYPE,
    EMPLOYMENT_MONTHS,
    COMPANY_SIZE,
    HOUSEHOLD_HOMELESS,
    LIVES_APART_FROM_PARENTS,
    PARENT_ON_HOUSING_BENEFIT,
    BIRTH_DATE,
    MILITARY_MONTHS,
    MONTHLY_INCOME,
    NET_ASSETS,
    AVAILABLE_CASH,
    EXISTING_JEONSE_LOAN,
    INCOME_SOURCE,
    ASSET_SOURCE,
    FINANCIAL_DATA_CONFIRMED
}
