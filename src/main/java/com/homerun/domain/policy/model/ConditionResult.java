package com.homerun.domain.policy.model;

import java.time.LocalDate;

/**
 * 조건 하나를 평가한 결과. {@code isMet} 이 null 이면 모름(NEED_INFO) — 소득·자산 금액이 아니라
 * 충족 여부만 담아서 그대로 {@code verdict_basis} 에 옮겨 쓸 수 있게 한다(SEC-01-01).
 *
 * <p>{@code eligibleUntil} 은 연령 상한처럼 시간이 지나면 저절로 사라지는 조건에서만 채운다
 * (POL-01-04, #110) — PASS/FAIL 과 무관하게 "언제까지 유효한 조건인지"를 담는다.
 */
public record ConditionResult(
        String code,
        String label,
        String requiredText,
        Boolean isMet,
        String factCode,
        String sourceUrl,
        LocalDate eligibleUntil) {

    /** eligibleUntil 이 없는 대부분의 조건을 위한 편의 생성자. */
    public ConditionResult(
            String code, String label, String requiredText, Boolean isMet, String factCode, String sourceUrl) {
        this(code, label, requiredText, isMet, factCode, sourceUrl, null);
    }
}
