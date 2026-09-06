package com.homerun.domain.property.type;

/**
 * 건축물대장 자동 판정(BR-10a, FR-P1-09). 저장하지 않고 이미 확인한 값들에서 파생한다 —
 * 신호등(TrafficLight)과 같은 방식이다.
 *
 * <p>차단은 신호등이 한다(근생·위반은 이미 RED). 이 판정은 화면에 무엇을 안내할지 가르는 요약이다.
 * 특히 CHECK_RESIDENTIAL 은 오피스텔의 "주거용 표기 확인 필요"를 OK 와 갈라 내기 위한 상태다.
 */
public enum BuildingVerdict {
    /** 위반건축물 또는 근린생활시설(비주거). 어떤 전세 상품도 불가(🔴). */
    ILLEGAL_BUILDING,
    /** 오피스텔 — 계약서·대장의 주거용 표기를 확인해야 한다(🟡). */
    CHECK_RESIDENTIAL,
    /** 다가구 — 취급 은행·보증기관·한도 제한 가능. 사전상담 확인(🟡). */
    MULTI_FAMILY,
    /** 건축물대장상 걸리는 것 없음. 등기부 확인 대기(🟡). */
    OK;

    /**
     * BR-10a 우선순위: 위반·근생(불가) → 오피스텔(주거용 확인) → 다가구(경고) → OK.
     *
     * <p>주용도 원문을 저장하지 않아 "업무시설 오피스텔"만 골라낼 수 없다. 오피스텔은 모두
     * 주거용 표기 확인 대상으로 둔다 — 오피스텔은 원래 주거용 표기를 확인해야 하고, 모르는 것을
     * OK 로 넘기지 않는다.
     * ponytail: 오피스텔 일괄 CHECK_RESIDENTIAL, 주용도 원문 저장 시 업무시설만으로 좁힐 수 있음.
     */
    public static BuildingVerdict of(
            Boolean violationBuilding, Boolean nonResidential, String houseType, Boolean multiHousehold) {
        if (Boolean.TRUE.equals(violationBuilding) || Boolean.TRUE.equals(nonResidential)) {
            return ILLEGAL_BUILDING;
        }
        if ("OFFICETEL".equals(houseType)) {
            return CHECK_RESIDENTIAL;
        }
        if (Boolean.TRUE.equals(multiHousehold)) {
            return MULTI_FAMILY;
        }
        return OK;
    }
}
