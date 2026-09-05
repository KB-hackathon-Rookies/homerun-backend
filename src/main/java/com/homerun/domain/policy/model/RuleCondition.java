package com.homerun.domain.policy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.homerun.domain.policy.type.HouseholdBasis;

/**
 * {@code policy_rule.rule_json} 안의 조건 하나.
 *
 * <p>표현은 최소 형태다 — "plan_input 의 field 를 op 로 비교한다"만 담는다. {@code factCode} 가
 * 있으면 비교 기준을 {@code config_effective} 에서 읽고, 없으면 {@code value} 리터럴과 비교한다.
 * 엔진이 모르는 {@code op} 는 전부 미지원으로 보고 NEED_INFO 로 떨어뜨린다(#85 PR 참고 — rule_json
 * 스키마는 엔진을 실제로 만들면서 정리된다고 못박아 뒀다).
 *
 * <p>{@code householdBasis} 는 이 조건을 어느 가구 기준으로 보는지다(POL-01-03). 적지 않은 조건은
 * {@link HouseholdBasis#SELF} 로 본다 — 지금까지의 모든 조건이 그 기준이라 기존 rule_json 을 하나도
 * 고치지 않아도 동작이 같다. 읽을 때는 {@link #effectiveBasis()} 를 쓴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleCondition(
        String code,
        String field,
        String op,
        Object value,
        @JsonProperty("fact_code") String factCode,
        @JsonProperty("adjust_field") String adjustField,
        @JsonProperty("alt_fact_code") String altFactCode,
        @JsonProperty("household_basis") HouseholdBasis householdBasis) {

    /** household_basis 없는 조건(기존 rule_json 전부)을 위한 편의 생성자. */
    public RuleCondition(
            String code,
            String field,
            String op,
            Object value,
            String factCode,
            String adjustField,
            String altFactCode) {
        this(code, field, op, value, factCode, adjustField, altFactCode, null);
    }

    /** alt_fact_code 도 household_basis 도 없는 조건을 위한 편의 생성자. */
    public RuleCondition(String code, String field, String op, Object value, String factCode, String adjustField) {
        this(code, field, op, value, factCode, adjustField, null, null);
    }

    /**
     * 판정에 실제로 쓸 가구 기준. 조건식에 안 적혀 있으면 독립가구로 본다.
     *
     * <p>null 을 그대로 흘리지 않는다 — "안 적혀 있음"과 "독립가구"는 판정에서 같은 뜻이라,
     * 읽는 쪽마다 null 을 다르게 해석하면 그게 곧 갈라지는 지점이 된다.
     */
    public HouseholdBasis effectiveBasis() {
        return householdBasis == null ? HouseholdBasis.SELF : householdBasis;
    }
}
