package com.homerun.domain.settlement.type;

/** 정착 항목 상태(FR-HD-01). */
public enum DashboardItemStatus {
    /** 끝난 것으로 확인됨. */
    DONE,
    /** 해야 할 일이 남았음(확인 가능). */
    PENDING,
    /** 완료 여부를 저장하지 않아 추적할 수 없음. 진행률에서 제외한다 -- 근거 없이 미완료로 단정하지 않는다. */
    NOT_TRACKED
}
