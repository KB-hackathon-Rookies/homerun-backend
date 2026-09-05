package com.homerun.domain.property.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "최종 매물과 해당 매물의 은행 상담 결과 선택")
public record PropertyDecisionRequest(
        @NotNull Long propertyId, @NotNull Long consultationId) {}
