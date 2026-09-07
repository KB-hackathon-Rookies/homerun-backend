package com.homerun.domain.contract.type;

/** 보증금 미반환 시 조치(BR-33). DB CHECK(ck_lease_end_unreturned_action)와 값이 같아야 한다. */
public enum UnreturnedAction {
    /** 임차권등기명령. */
    LEASEHOLD_REGISTRATION,
    /** 반환보증 이행청구. */
    GUARANTEE_CLAIM,
    /** 보증금반환청구소송. */
    LAWSUIT
}
