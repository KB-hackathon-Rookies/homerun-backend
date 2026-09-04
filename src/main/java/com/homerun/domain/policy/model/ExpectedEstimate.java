package com.homerun.domain.policy.model;

import java.math.BigDecimal;

/**
 * 예상 대출 스펙. amount 스펙·금액 기준이 없거나 FAIL이면 전부 null이다.
 * 금리만 미확인일 때는 금액을 유지하고 금리·월이자만 null로 둔다.
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
