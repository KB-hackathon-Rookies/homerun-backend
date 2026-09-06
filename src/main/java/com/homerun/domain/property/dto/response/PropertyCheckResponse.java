package com.homerun.domain.property.dto.response;

import com.homerun.domain.property.entity.PropertyCheck;
import com.homerun.domain.property.type.CheckResult;
import java.time.Instant;

public record PropertyCheckResponse(
        String code, String label, CheckResult result, String factCode, String sourceUrl, Instant checkedAt) {

    public static PropertyCheckResponse from(PropertyCheck check) {
        return new PropertyCheckResponse(
                check.getCheckCode(),
                check.getCheckLabel(),
                check.getResult(),
                check.getFactCode(),
                check.getSourceUrl(),
                check.getCheckedAt());
    }
}
