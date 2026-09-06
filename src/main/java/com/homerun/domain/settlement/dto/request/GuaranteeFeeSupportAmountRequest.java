package com.homerun.domain.settlement.dto.request;

import com.homerun.domain.settlement.type.GuaranteeFeeSupportCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

/**
 * 보증료 지원 금액 계산 입력(BR-31).
 *
 * @param category 지원 대상 구분(자격 판정 결과에서 정한다)
 * @param guaranteeFeePaid 실제 납부한 반환보증 보증료(원)
 * @param enrolledAt 반환보증 가입일. 한도(40/30만)를 가른다
 */
@Schema(description = "보증료 지원 금액 계산 입력(BR-31)")
public record GuaranteeFeeSupportAmountRequest(
        @NotNull GuaranteeFeeSupportCategory category,
        @NotNull @PositiveOrZero Long guaranteeFeePaid,
        @NotNull LocalDate enrolledAt) {}
