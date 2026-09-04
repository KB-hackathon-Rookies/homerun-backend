package com.homerun.domain.policy.type;

/** {@code policy_verdict.verdict}. DB CHECK 제약(ck_verdict)과 값이 같아야 한다. */
public enum PolicyVerdictResult {
    PASS,
    NEED_INFO,
    FAIL
}
