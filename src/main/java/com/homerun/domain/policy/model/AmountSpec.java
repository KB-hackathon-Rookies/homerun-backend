package com.homerun.domain.policy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code policy_rule.rule_json.amount} — 예상 대출액 계산에 쓰는 팩트 코드.
 * {@code hope_deposit × ratio} 와 {@code cap} 중 작은 쪽이 예상 대출액이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AmountSpec(
        @JsonProperty("ratio_fact_code") String ratioFactCode,
        @JsonProperty("cap_fact_code") String capFactCode,
        @JsonProperty("non_capital_cap_fact_code") String nonCapitalCapFactCode) {
    public AmountSpec(String ratioFactCode, String capFactCode) {
        this(ratioFactCode, capFactCode, null);
    }
}
