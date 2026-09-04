package com.homerun.domain.policy.dto.response;

import com.homerun.domain.policy.model.ConditionResult;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record ConditionBasisResponse(
        String code,
        String label,
        String requiredText,
        Boolean isMet,
        String factCode,
        String sourceUrl,
        LocalDate eligibleUntil,
        Long daysRemaining) {

    /** daysRemaining 은 오늘 기준 D-day다 — eligibleUntil 이 없으면 같이 null, 이미 지났으면
     * 음수(POL-01-04). "오늘"을 밖에서 받아 판정 시각과 응답 시각이 갈리지 않게 한다. */
    public static ConditionBasisResponse from(ConditionResult result, LocalDate today) {
        LocalDate eligibleUntil = result.eligibleUntil();
        Long daysRemaining = eligibleUntil == null ? null : ChronoUnit.DAYS.between(today, eligibleUntil);
        return new ConditionBasisResponse(
                result.code(),
                result.label(),
                result.requiredText(),
                result.isMet(),
                result.factCode(),
                result.sourceUrl(),
                eligibleUntil,
                daysRemaining);
    }
}
