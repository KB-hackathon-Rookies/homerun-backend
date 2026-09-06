package com.homerun.domain.contract.type;

/** 계약 진행 단계(FR-P8-04). 해제 가능성이 이 단계로 갈린다. */
public enum PaymentStage {
    /** 계약금만 지급. 해약금 규정으로 해제 가능. */
    DOWN_PAYMENT,
    /** 중도금 지급 후. 상대방 동의가 있어야 해제된다. */
    INTERIM,
    /** 잔금 지급 후. 계약이 완결돼 해제할 수 없다. */
    BALANCE
}
