package com.homerun.domain.settlement.type;

/** 연체 위험 상태(FR-H5-04). */
public enum DelinquencyStatus {
    /** 월 잔여금이 0 이상이라 상환 여력이 있다. */
    OK,
    /** 월 잔여금이 마이너스(적자)라 연체 위험이 있다. */
    AT_RISK,
    /** 이자 고정지출이 등록되지 않아 판단할 수 없다. 등록부터 해야 한다. */
    NEEDS_FIXED_EXPENSE
}
