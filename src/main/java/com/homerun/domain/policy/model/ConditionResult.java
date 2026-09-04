package com.homerun.domain.policy.model;

/**
 * 조건 하나를 평가한 결과. {@code isMet} 이 null 이면 모름(NEED_INFO) — 소득·자산 금액이 아니라
 * 충족 여부만 담아서 그대로 {@code verdict_basis} 에 옮겨 쓸 수 있게 한다(SEC-01-01).
 */
public record ConditionResult(
        String code, String label, String requiredText, Boolean isMet, String factCode, String sourceUrl) {}
