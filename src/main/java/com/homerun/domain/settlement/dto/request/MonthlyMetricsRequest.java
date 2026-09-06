package com.homerun.domain.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * 월간 지표 계산 입력(BR-28). 계획값이든 실제값이든 같은 입력으로 계산한다.
 *
 * @param loanAmount 대출금(원)
 * @param annualRatePercent 연 금리(퍼센트, 2.2% → 2.2)
 * @param managementFee 월 관리비(원)
 * @param monthlyIncome 월 소득(원)
 * @param livingCost 월 생활비(원)
 */
@Schema(description = "월간 지표 계산 입력(BR-28)")
public record MonthlyMetricsRequest(
        @NotNull @PositiveOrZero Long loanAmount,
        @NotNull @PositiveOrZero BigDecimal annualRatePercent,
        @NotNull @PositiveOrZero Long managementFee,
        @NotNull @PositiveOrZero Long monthlyIncome,
        @NotNull @PositiveOrZero Long livingCost) {}
