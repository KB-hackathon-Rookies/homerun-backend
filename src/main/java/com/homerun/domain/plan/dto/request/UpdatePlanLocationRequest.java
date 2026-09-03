package com.homerun.domain.plan.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdatePlanLocationRequest {

    @NotBlank
    private String stepCode;

    @NotBlank
    private String taskCode;
}