package com.homerun.domain.loan.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "전세대출 가능성 및 예상 자금 스펙")
public record JeonseLoanDiagnosisResponse(
        Long planId,
        Long targetDeposit,
        Long availableCash,
        Long recommendedDepositLimit,
        Long estimatedMaxLoan,
        boolean incomplete,
        List<LoanProductDiagnosisResponse> products,
        String disclaimer,
        Instant evaluatedAt) {

    public JeonseLoanDiagnosisResponse {
        products = List.copyOf(products);
    }
}
