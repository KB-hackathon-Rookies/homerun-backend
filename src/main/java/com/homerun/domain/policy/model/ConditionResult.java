package com.homerun.domain.policy.model;

import com.homerun.domain.policy.type.HouseholdBasis;
import java.time.LocalDate;

/**
 * 조건 하나를 평가한 결과. {@code isMet} 이 null 이면 모름(NEED_INFO) — 소득·자산 금액이 아니라
 * 충족 여부만 담아서 그대로 {@code verdict_basis} 에 옮겨 쓸 수 있게 한다(SEC-01-01).
 *
 * <p>{@code eligibleUntil} 은 연령 상한처럼 시간이 지나면 저절로 사라지는 조건에서만 채운다
 * (POL-01-04, #110) — PASS/FAIL 과 무관하게 "언제까지 유효한 조건인지"를 담는다.
 *
 * <p>{@code householdBasis} 는 이 조건을 어느 가구 기준으로 봤는지다(POL-01-03). 가구 기준이
 * 다르면 같은 사람도 결과가 뒤집히므로, 근거를 보여줄 때 무엇을 기준으로 본 조건인지가 함께
 * 가야 한다. 금액이 아니라 기준의 이름이라 SEC-01-01 과 무관하다.
 */
public record ConditionResult(
        String code,
        String label,
        String requiredText,
        Boolean isMet,
        String factCode,
        String sourceUrl,
        LocalDate eligibleUntil,
        HouseholdBasis householdBasis) {

    /** eligibleUntil 이 없는 대부분의 조건을 위한 편의 생성자. */
    public ConditionResult(
            String code,
            String label,
            String requiredText,
            Boolean isMet,
            String factCode,
            String sourceUrl,
            HouseholdBasis householdBasis) {
        this(code, label, requiredText, isMet, factCode, sourceUrl, null, householdBasis);
    }

    /**
     * 가구 기준이 의미 없는 조건을 위한 편의 생성자 — {@code RULE_NOT_ACTIVE} 처럼 자격 조건이
     * 아니라 판정을 못 한 사정을 담는 것들이다. 이런 조건에 SELF 를 박으면 독립가구 기준으로
     * 따져 본 것처럼 읽히므로 비워 둔다.
     */
    public ConditionResult(
            String code, String label, String requiredText, Boolean isMet, String factCode, String sourceUrl) {
        this(code, label, requiredText, isMet, factCode, sourceUrl, null, null);
    }
}
