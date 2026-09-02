package com.homerun.domain.contract.type;

/** 계약 진행 단계 하나의 상태(PRP-02-04). */
public enum StepStatus {
    /** 끝났다. */
    DONE,
    /** 지금 할 차례다. 끝나지 않은 것 중 가장 앞선 하나뿐이다. */
    CURRENT,
    /** 아직 차례가 아니다. */
    PENDING
}
