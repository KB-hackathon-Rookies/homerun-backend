package com.homerun.domain.policy.dto.response;

import com.homerun.domain.policy.type.RejectionReasonCategory;

public record RejectionReasonResponse(
        String reasonCode,
        String reasonLabel,
        RejectionReasonCategory category,
        String alternativePolicyCode,
        String alternativePolicyName) {}
