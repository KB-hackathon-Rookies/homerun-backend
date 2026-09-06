package com.homerun.domain.property.type;

/**
 * 임대인 전세대출 협조 여부(FR-P1-07). {@code property.ck_property_landlord_consent} 와 값이 같아야 한다.
 *
 * <p>전세대출은 임대인이 질권설정·채권양도 통지에 협조해야 진행된다. 거부(REFUSED)라도 신호등을
 * RED 로 만들지 않는다(BR-10) — 설득으로 뒤집히기도 하고, 그 매물을 포기할지는 사용자가 정한다.
 */
public enum LandlordConsent {
    /** 확인했고 협조 가능. */
    CONFIRMED,
    /** 아직 안 물어봤다. 은행 상담 전에 확인하도록 안내한다. */
    NOT_ASKED,
    /** 거부 의사를 밝혔다. 설득 스크립트를 제공하되 다른 매물도 권한다. */
    REFUSED
}
