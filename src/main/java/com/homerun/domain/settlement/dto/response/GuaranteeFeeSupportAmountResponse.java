package com.homerun.domain.settlement.dto.response;

import com.homerun.domain.settlement.type.GuaranteeFeeSupportCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 보증료 지원 예상 금액(BR-31, FR-H2-01). 자격 판정은 2루(GTE-01-04)에서 하고, 여기서는 금액만 낸다.
 *
 * @param category 지원 대상 구분
 * @param supportRatePercent 적용 지원율(퍼센트). 청년·신혼 100, 청년 외 90
 * @param ceiling 가입일 기준 지원 한도(원)
 * @param supportAmount 예상 지원액(원) = min(보증료 × 지원율, 한도)
 */
@Schema(description = "보증료 지원 예상 금액(BR-31)")
public record GuaranteeFeeSupportAmountResponse(
        GuaranteeFeeSupportCategory category, BigDecimal supportRatePercent, long ceiling, long supportAmount) {}
