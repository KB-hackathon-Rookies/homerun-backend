package com.homerun.domain.property.type;

/**
 * 어디서 거절됐는가(BR-24, DR-11). 전세대출은 은행 심사와 보증기관 심사를 모두 통과해야 한다.
 * DB CHECK 제약(ck_bank_consultation_rejection_stage)과 값이 같아야 한다.
 *
 * <p>application 도메인의 RejectStage 와 다른 축이다 — 저쪽은 신청(application) 단위,
 * 이쪽은 은행 상담(bank_consultation) 단위이며 값도 다르다(NOT_TOLD 포함).
 */
public enum RejectionStage {
    /** 은행 자체 심사. */
    BANK,
    /** 보증기관 심사. */
    GUARANTEE,
    /** 어디서 막혔는지 못 들음. */
    NOT_TOLD
}
