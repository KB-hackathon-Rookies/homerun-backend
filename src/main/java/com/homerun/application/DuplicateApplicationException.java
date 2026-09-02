package com.homerun.application;

/** 같은 계획에서 같은 정책을 두 번 신청하려 할 때. */
public class DuplicateApplicationException extends RuntimeException {

    DuplicateApplicationException(Long policyId) {
        super("이미 신청한 정책이다: " + policyId);
    }
}
