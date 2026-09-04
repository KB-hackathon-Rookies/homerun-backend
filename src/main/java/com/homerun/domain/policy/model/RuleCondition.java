package com.homerun.domain.policy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code policy_rule.rule_json} 안의 조건 하나.
 *
 * <p>표현은 최소 형태다 — "plan_input 의 field 를 op 로 비교한다"만 담는다. {@code factCode} 가
 * 있으면 비교 기준을 {@code config_effective} 에서 읽고, 없으면 {@code value} 리터럴과 비교한다.
 * 엔진이 모르는 {@code op} 는 전부 미지원으로 보고 NEED_INFO 로 떨어뜨린다(#85 PR 참고 — rule_json
 * 스키마는 엔진을 실제로 만들면서 정리된다고 못박아 뒀다).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleCondition(
        String code,
        String field,
        String op,
        Object value,
        @JsonProperty("fact_code") String factCode,
        @JsonProperty("adjust_field") String adjustField,
        @JsonProperty("alt_fact_code") String altFactCode) {

    /** alt_fact_code 없는 대부분의 조건(기존 rule_json 전부)을 위한 편의 생성자. */
    public RuleCondition(String code, String field, String op, Object value, String factCode, String adjustField) {
        this(code, field, op, value, factCode, adjustField, null);
    }
}
