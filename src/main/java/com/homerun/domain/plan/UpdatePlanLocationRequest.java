package com.homerun.domain.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePlanLocationRequest(
        @NotBlank @Size(max = 100) @Schema(description = "마지막 기능 또는 화면 코드", example = "DIA_INCOME")
        String locationCode) {}
