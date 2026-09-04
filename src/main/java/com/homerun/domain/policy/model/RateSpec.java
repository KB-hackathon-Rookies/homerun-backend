package com.homerun.domain.policy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code policy_rule.rule_json.rate} — 예상 금리 범위 계산에 쓰는 팩트 코드. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RateSpec(
        @JsonProperty("min_fact_code") String minFactCode,
        @JsonProperty("max_fact_code") String maxFactCode) {}
