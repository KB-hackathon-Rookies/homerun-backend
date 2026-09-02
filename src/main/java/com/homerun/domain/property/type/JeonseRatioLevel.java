package com.homerun.domain.property.type;

/**
 * 전세가율 구간(FCT-119).
 *
 * <p>확정도가 REVIEW 라 화면에 변경 가능 표시가 필요하다. 컷오프의 공식 출처를 아직 못 찾았다.
 */
public enum JeonseRatioLevel {
    /** ~70%. */
    SAFE,
    /** 70~80%. */
    CAUTION,
    /** 80% 초과. 깡통전세 가능성. */
    RISK,
    /** 시세나 선순위채권을 모르면 계산할 수 없다. */
    UNKNOWN
}
