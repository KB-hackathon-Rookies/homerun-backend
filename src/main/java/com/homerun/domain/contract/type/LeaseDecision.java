package com.homerun.domain.contract.type;

/** 계약 종료 시 결정(FR-H9-01). DB CHECK 제약(ck_lease_end_decision)과 값이 같아야 한다. */
public enum LeaseDecision {
    /** 갱신한다. */
    RENEW,
    /** 퇴거한다. */
    LEAVE,
    /** 아직 정하지 않음. */
    UNDECIDED
}
