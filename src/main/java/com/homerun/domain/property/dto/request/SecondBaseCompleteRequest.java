package com.homerun.domain.property.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record SecondBaseCompleteRequest(
        @Positive int expectedDecisionRevision, @NotBlank String ruleVersion) {}
