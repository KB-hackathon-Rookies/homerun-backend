package com.homerun.domain.diagnosis.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "1루 진단 비용 입력. 대출 한도와 이자는 서버가 정책 판정 결과로 계산합니다.")
public record FirstBaseCostRequest(
        @PositiveOrZero @Schema(description = "이사 비용", example = "1000000")
        long movingCost,

        @PositiveOrZero @Schema(description = "중개보수", example = "600000")
        long brokerageFee,

        @PositiveOrZero @Schema(description = "반환보증 등 보증료", example = "300000")
        long guaranteeFee,

        @PositiveOrZero @Schema(description = "인지세 본인 부담액", example = "75000")
        long stampTax,

        @PositiveOrZero @Schema(description = "입주 후 생활 예비비", example = "3000000")
        long emergencyReserve,

        @PositiveOrZero @Schema(description = "월 생활비", example = "900000")
        long monthlyLivingExpense,

        @PositiveOrZero @Schema(description = "월 대출 상환액. 생략하면 최근 오픈뱅킹 스냅샷 사용")
        Long monthlyDebtPayment) {

    public DiagnosisCalculationRequest toCalculation(long expectedLoanAmount, long expectedMonthlyInterest) {
        return new DiagnosisCalculationRequest(
                movingCost,
                brokerageFee,
                guaranteeFee,
                stampTax,
                emergencyReserve,
                monthlyLivingExpense,
                monthlyDebtPayment,
                expectedLoanAmount,
                expectedMonthlyInterest);
    }
}
