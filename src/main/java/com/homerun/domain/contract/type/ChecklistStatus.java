package com.homerun.domain.contract.type;

/** 계약 전 체크리스트 항목 하나의 상태(PRP-02-02). */
public enum ChecklistStatus {
    /** 확인이 끝났고 문제가 없다. */
    DONE,
    /** 확인했는데 문제가 있다. 계약금을 넣기 전에 해결해야 한다. */
    BLOCKED,
    /** 아직 확인하지 않았다. */
    TODO
}
