package com.homerun.domain.policy.dto.response;

import com.homerun.domain.policy.model.ExpectedEstimate;
import java.math.BigDecimal;

/** amount/rate 스펙이 없는 정책(반환보증 등)이나 FAIL 판정이면 null. */
public record LoanEstimateResponse(
        Long recommendedDepositLimit,
        Long estimatedLoanAmount,
        Long ownFundsRequired,
        BigDecimal rateMin,
        BigDecimal rateMax,
        Long monthlyInterestMin,
        Long monthlyInterestMax) {

    public static LoanEstimateResponse from(ExpectedEstimate estimate) {
        if (estimate.isEmpty()) {
            return null;
        }
        return new LoanEstimateResponse(
                estimate.recommendedDepositLimit(),
                estimate.estimatedLoanAmount(),
                estimate.ownFundsRequired(),
                estimate.rateMin(),
                estimate.rateMax(),
                estimate.monthlyInterestMin(),
                estimate.monthlyInterestMax());
    }
}
