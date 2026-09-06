package com.homerun.domain.settlement.dto.response;

import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.settlement.entity.LoanAccount;
import com.homerun.domain.settlement.type.RepaymentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 실행 대출 조회(DR-20). 저장값에 더해 월 이자를 파생해 준다(BR-28 = 원금 × 금리 ÷ 12).
 */
@Schema(description = "실행 대출 계좌(DR-20)")
public record LoanAccountResponse(
        Long planId,
        ConsultedLoanProduct product,
        CollateralMethod guarantee,
        long principal,
        BigDecimal rate,
        RepaymentType repaymentType,
        LocalDate executedAt,
        LocalDate maturityAt,
        LocalDate preferentialUntil,
        int extensionCount,
        long monthlyInterest) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE = new BigDecimal("12");

    public static LoanAccountResponse from(LoanAccount loan) {
        long monthlyInterest = BigDecimal.valueOf(loan.getPrincipal())
                .multiply(loan.getRate().divide(HUNDRED, 10, RoundingMode.HALF_UP))
                .divide(TWELVE, 0, RoundingMode.HALF_UP)
                .longValueExact();
        return new LoanAccountResponse(
                loan.getPlanId(),
                loan.getProduct(),
                loan.getGuarantee(),
                loan.getPrincipal(),
                loan.getRate(),
                loan.getRepaymentType(),
                loan.getExecutedAt(),
                loan.getMaturityAt(),
                loan.getPreferentialUntil(),
                loan.getExtensionCount(),
                monthlyInterest);
    }
}
