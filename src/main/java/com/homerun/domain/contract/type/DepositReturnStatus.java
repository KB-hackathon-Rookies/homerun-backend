package com.homerun.domain.contract.type;

/** 보증금 반환 여부(FR-HX-01). DB CHECK(ck_lease_end_deposit_returned)와 값이 같아야 한다. */
public enum DepositReturnStatus {
    /** 전액 반환. */
    YES,
    /** 미반환. */
    NO,
    /** 일부만 반환. */
    PARTIAL
}
