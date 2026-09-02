package com.homerun.domain.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CompletePlanStepRequest(
        @NotBlank @Schema(description = "클라이언트가 판정에 사용한 규칙 버전", example = "1.0")
        String ruleVersion) {}
