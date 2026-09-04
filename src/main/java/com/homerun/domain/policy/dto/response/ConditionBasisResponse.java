package com.homerun.domain.policy.dto.response;

import com.homerun.domain.policy.model.ConditionResult;

public record ConditionBasisResponse(
        String code, String label, String requiredText, Boolean isMet, String factCode, String sourceUrl) {

    public static ConditionBasisResponse from(ConditionResult result) {
        return new ConditionBasisResponse(
                result.code(),
                result.label(),
                result.requiredText(),
                result.isMet(),
                result.factCode(),
                result.sourceUrl());
    }
}
