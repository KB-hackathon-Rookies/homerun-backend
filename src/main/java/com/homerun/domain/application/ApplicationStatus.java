package com.homerun.domain.application;

/** 신청 진행 상태. application.ck_app_status 와 값이 같아야 한다. */
public enum ApplicationStatus {
    PREPARING,
    SUBMITTED,
    SCREENING,
    APPROVED,
    REJECTED,
    CANCELED
}
