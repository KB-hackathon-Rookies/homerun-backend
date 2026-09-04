package com.homerun.domain.policy.model;

import java.math.BigDecimal;

/**
 * 예상 대출 스펙. 값이 없으면(정책에 amount/rate 스펙이 없거나, 필요한 입력·팩트가 없거나,
 * verdict 가 FAIL 이면) 전부 null 이다 — 안 되는 정책에 금액을 붙여 보여주지 않는다.
 */
public record ExpectedEstimate(
        Long recommendedDepositLimit,
        Long estimatedLoanAmount,
        Long ownFundsRequired,
        BigDecimal rateMin,
        BigDecimal rateMax,
        Long monthlyInterestMin,
        Long monthlyInterestMax) {

    public static ExpectedEstimate empty() {
        return new ExpectedEstimate(null, null, null, null, null, null, null);
    }

    public boolean isEmpty() {
        return estimatedLoanAmount == null;
    }
}
