package com.homerun.domain.contract.type;

/** 계약 해제 가능성(FR-P8-04). */
public enum CancelFeasibility {
    /** 계약금만 지급 — 해약금 규정으로 해제할 수 있다(계약금 포기 또는 배액 상환). */
    REFUNDABLE_BY_PENALTY,
    /** 중도금 지급 후 — 상대방 동의가 있어야 한다. */
    NEEDS_COUNTERPARTY_CONSENT,
    /** 잔금 지급 후 — 해제할 수 없다. */
    NOT_POSSIBLE
}
