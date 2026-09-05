package com.homerun.domain.property.dto.request;

import com.homerun.domain.property.type.CollateralMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "매물별 은행 사전상담 결과")
public record BankConsultationRequest(
        @NotBlank String bankName,
        String branchName,
        Long policyId,
        Long guaranteeAgencyId,
        @NotNull CollateralMethod collateralMethod,
        @PositiveOrZero Long approvedLimit,

        @DecimalMin(value = "0.0", message = "상담 금리는 0 이상이어야 합니다")
        BigDecimal quotedRate,

        @NotNull LocalDate consultedAt,
        String memo) {}
