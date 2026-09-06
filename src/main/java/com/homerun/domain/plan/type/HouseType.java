package com.homerun.domain.plan.type;

import com.homerun.global.external.realestate.HousingType;

/**
 * 주택 유형. 도메인 전체가 쓰는 단일 어휘다 — 1루 희망유형, 매물, 계약이 모두 이것을 쓴다.
 *
 * <p>실거래 조회 계층의 {@link HousingType}(아파트·오피스텔·연립다세대 3종)과는 다르다. 그쪽은
 * 국토부 실거래 API 엔드포인트에 묶인 사실이라 없앨 수 없고, 여기가 그보다 넓다(단독·다가구
 * 등은 실거래를 유형별로 조회하지 않는다). 두 어휘를 잇는 곳은 {@link #toHousingType()} 하나뿐이다.
 */
public enum HouseType {
    APARTMENT,
    OFFICETEL,
    /** 연립·다세대. 실거래 API 의 ROW_HOUSE 와 같은 것을 도메인에서는 이 이름으로 부른다. */
    VILLA,
    MULTI_FAMILY,
    DETACHED,
    OTHER;

    /**
     * 실거래를 유형별로 조회할 수 있으면 그 {@link HousingType} 을, 아니면 비어 있음.
     *
     * <p>단독·다가구·기타는 국토부가 유형별 실거래 API 를 제공하지 않는다 — 비어 있음을 받은
     * 쪽은 실거래 매칭을 건너뛰고 면적을 직접 입력받는다(FR-P1-10).
     */
    public java.util.Optional<HousingType> toHousingType() {
        return switch (this) {
            case APARTMENT -> java.util.Optional.of(HousingType.APARTMENT);
            case OFFICETEL -> java.util.Optional.of(HousingType.OFFICETEL);
            case VILLA -> java.util.Optional.of(HousingType.ROW_HOUSE);
            case MULTI_FAMILY, DETACHED, OTHER -> java.util.Optional.empty();
        };
    }

    /** 실거래 API 어휘를 도메인 어휘로 되돌린다. 자동판별 결과를 도메인으로 넘길 때 쓴다. */
    public static HouseType from(HousingType housingType) {
        return switch (housingType) {
            case APARTMENT -> APARTMENT;
            case OFFICETEL -> OFFICETEL;
            case ROW_HOUSE -> VILLA;
        };
    }
}
