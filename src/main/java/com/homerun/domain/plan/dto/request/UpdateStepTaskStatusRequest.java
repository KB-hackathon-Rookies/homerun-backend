package com.homerun.domain.plan.dto.request;

import com.homerun.domain.plan.type.StepTaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateStepTaskStatusRequest(
        @NotNull StepTaskStatus status, @NotBlank String ruleVersion) {}
