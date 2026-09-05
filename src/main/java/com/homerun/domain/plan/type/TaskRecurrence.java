package com.homerun.domain.plan.type;

/** 홈 단계에서 반복되는 할 일의 주기. ONCE는 반복하지 않는 일반 작업이다. */
public enum TaskRecurrence {
    ONCE,
    MONTHLY,
    ANNUAL,
    MATURITY
}
