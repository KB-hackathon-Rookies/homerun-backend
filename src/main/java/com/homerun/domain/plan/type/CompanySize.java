package com.homerun.domain.plan.type;

/** 기업 규모. 중소기업은 버팀목 우대금리와 청년 적금 우대 양쪽에 걸린다(DIA-01-09). */
public enum CompanySize {
    LARGE,
    MID_SIZE,
    SMALL,
    PUBLIC,
    STARTUP,
    OTHER;

    /**
     * 병역 복무기간만큼 청년 버팀목 연령 상한을 늘려 주는 대상인가(BR-01). 중소·중견 재직 또는
     * 창업지원(중진공·신보·기보)만 해당한다. 대기업·공무원·공기업·기타는 제외하고, 모르면 늘리지
     * 않는다 — 근거 없이 상한을 늘리면 잘못된 통과가 된다.
     */
    public boolean qualifiesForMilitaryAgeExtension() {
        return this == SMALL || this == MID_SIZE || this == STARTUP;
    }

    /**
     * 청년 버팀목 "중소·중견기업 취업(창업) 청년" 우대금리 0.3%p 대상인가(FCT-255). 병역 연령 보정과
     * 같은 기업군(중소·중견·창업지원)이라 판단 기준이 같다. 대기업·공무원·공기업·기타는 제외한다.
     */
    public boolean qualifiesForYouthEmploymentRatePreference() {
        return this == SMALL || this == MID_SIZE || this == STARTUP;
    }
}
