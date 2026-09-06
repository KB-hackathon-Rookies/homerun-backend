package com.homerun.domain.property.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "매물 STEP 3 위반건축물 수기 확인")
public record PropertyViolationStepRequest(
        @Min(1) int expectedRevision,

        @NotNull @Schema(description = "정부24 건축물대장을 직접 확인한 결과")
        Boolean violationBuilding) {}
