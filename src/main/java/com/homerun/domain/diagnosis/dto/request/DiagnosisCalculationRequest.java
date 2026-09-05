package com.homerun.domain.diagnosis.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

public record DiagnosisCalculationRequest(
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

        @PositiveOrZero @Schema(description = "월 대출 상환액. 생략하면 최근 오픈뱅킹 스냅샷을 사용하며, 조회값도 미확인 정보로 표시")
        Long monthlyDebtPayment,

        @PositiveOrZero @Schema(description = "정책 적용 예상 대출액", example = "120000000")
        long expectedLoanAmount,

        @PositiveOrZero @Schema(description = "예상 월 이자. 정책 카드의 월 이자 예상값을 사용", example = "275000")
        long expectedMonthlyInterest) {}
