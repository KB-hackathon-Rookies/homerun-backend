package com.homerun.domain.plan.dto.request;

import com.homerun.domain.plan.type.FinancialIncomeAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "금융 소득 확인 요청")
public record FinancialIncomeConfirmationRequest(
        @NotNull @Schema(description = "오픈뱅킹 값 그대로 확인 또는 수동 금액 사용")
        FinancialIncomeAction action,

        @PositiveOrZero
        @Schema(description = "USE_MANUAL에서만 필수인 월소득 원 단위 금액. CONFIRM_OPEN_BANKING에서는 생략", example = "3000000")
        Long monthlyIncome) {}
