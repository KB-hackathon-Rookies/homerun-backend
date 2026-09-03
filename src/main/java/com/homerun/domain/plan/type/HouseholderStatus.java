package com.homerun.domain.plan.type;

/** 세대주 여부. 정책 다수가 "세대주" 를 요건으로 두고, 예비 세대주도 인정하는 경우가 있다. */
public enum HouseholderStatus {
    CURRENT,
    EXPECTED,
    NOT_HOUSEHOLDER
}
