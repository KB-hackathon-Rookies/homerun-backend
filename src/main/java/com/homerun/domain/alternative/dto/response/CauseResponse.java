package com.homerun.domain.alternative.dto.response;

import com.homerun.domain.policy.type.RejectionReasonCategory;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "미충족 원인 하나(ALT-01-01). FAIL 판정 하나의 탈락 사유에 대응한다")
public record CauseResponse(
        String policyCode,
        String policyName,
        String reasonCode,
        String reasonLabel,

        @Schema(description = "사용자/주택/한도 중 어느 것 때문에 불가한지(POL-03-10과 같은 분류). 맵에 없으면 null")
        RejectionReasonCategory category,

        String alternativePolicyCode,
        String alternativePolicyName) {}
