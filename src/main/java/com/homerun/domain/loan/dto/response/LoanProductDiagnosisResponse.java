package com.homerun.domain.loan.dto.response;

import com.homerun.domain.loan.type.JeonseLoanProduct;
import com.homerun.domain.loan.type.LoanDiagnosisStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "전세대출 상품별 예상 진단")
public record LoanProductDiagnosisResponse(
        JeonseLoanProduct product,
        String productName,
        LoanDiagnosisStatus status,
        Long depositLimit,
        Long estimatedLoanAmount,
        Long ownFundsRequired,
        BigDecimal expectedRateMin,
        BigDecimal expectedRateMax,
        Long monthlyInterestMin,
        Long monthlyInterestMax,
        boolean criteriaProvisional,
        List<String> reasons,
        String sourceUrl) {

    public LoanProductDiagnosisResponse {
        reasons = List.copyOf(reasons);
    }
}
