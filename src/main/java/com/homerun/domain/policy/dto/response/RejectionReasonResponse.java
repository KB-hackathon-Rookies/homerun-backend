package com.homerun.domain.policy.dto.response;

public record RejectionReasonResponse(
        String reasonCode, String reasonLabel, String alternativePolicyCode, String alternativePolicyName) {}
