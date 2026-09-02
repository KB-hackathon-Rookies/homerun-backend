package com.homerun.domain.property.type;

/** 검증 항목 하나의 결과. property_check.result 와 값이 같아야 한다. */
public enum CheckResult {
    /** 문제 없음. */
    PASS,
    /** 진행은 되지만 알고 있어야 하는 것. */
    WARN,
    /** 이 집으로는 대출·보증이 안 된다. 계약 전에 걸러야 한다. */
    BLOCK,
    /** 확인할 자료가 없다. 불가로 단정하지 않는다(NFR-01-06). */
    UNKNOWN
}
