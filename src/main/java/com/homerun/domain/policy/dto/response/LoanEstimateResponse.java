package com.homerun.domain.policy.dto.response;

import com.homerun.domain.policy.model.ExpectedEstimate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** amount/rate 스펙이 없는 정책(반환보증 등)이나 FAIL 판정이면 null. */
public record LoanEstimateResponse(
        @Schema(description = "사용 가능 현금·대출 비율·대출 한도·확인된 보증금 상한으로 계산한 예상 보증금. 현금/상한 미확인 시 null")
        Long recommendedDepositLimit,

        Long estimatedLoanAmount,
        Long ownFundsRequired,

        @Schema(description = "검증 가능한 금리 범위가 없으면 null. 금리 미확인 시에도 금액은 제공 가능")
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
