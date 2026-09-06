package com.homerun.domain.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * 소득공제 계산 입력(BR-29). loan 엔티티가 아직 없어 대출 조건을 요청으로 받는다.
 *
 * @param loanAmount 대출금(원)
 * @param annualRatePercent 연 금리(퍼센트, 2.2% → 2.2)
 * @param maturityLumpSum 만기일시상환인가. true 면 공제 대상은 연 이자뿐이다
 * @param annualRepayment 원리금균등 등 만기일시가 아닐 때의 연 상환액(원). 만기일시면 무시
 */
@Schema(description = "소득공제 계산 입력(BR-29)")
public record TaxDeductionRequest(
        @NotNull @PositiveOrZero Long loanAmount,
        @NotNull @PositiveOrZero BigDecimal annualRatePercent,
        boolean maturityLumpSum,
        @PositiveOrZero Long annualRepayment) {}
