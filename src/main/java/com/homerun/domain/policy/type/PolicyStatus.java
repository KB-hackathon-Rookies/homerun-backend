package com.homerun.domain.policy.type;

/** {@code policy.status}. DB CHECK 제약(ck_policy_status)과 값이 같아야 한다. */
public enum PolicyStatus {
    ACTIVE,
    DISCONTINUED,
    DRAFT
}
