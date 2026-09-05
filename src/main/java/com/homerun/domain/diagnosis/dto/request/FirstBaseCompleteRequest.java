package com.homerun.domain.diagnosis.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "1루 최종 제출 요청")
public record FirstBaseCompleteRequest(
        @Min(1) @Schema(description = "STEP 입력 완료 시점의 plan_input revision", example = "9")
        int expectedRevision,

        @NotBlank @Schema(description = "계획 규칙 버전", example = "1.0")
        String ruleVersion,

        @Valid @NotNull FirstBaseCostRequest calculation) {}
