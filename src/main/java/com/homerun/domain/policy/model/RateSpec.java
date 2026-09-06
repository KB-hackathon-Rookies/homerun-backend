package com.homerun.domain.policy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * {@code policy_rule.rule_json.rate} — 예상 금리 계산에 쓰는 팩트 코드.
 *
 * <p>두 가지 모드가 있다. 일반버팀목·서울시처럼 소득구간표를 확보하지 못한 정책은 {@code min/max}
 * 팩트로 <b>범위</b>를 그대로 보여 준다. 청년버팀목처럼 확정된 소득구간표가 있는 정책은
 * {@code income_bands} 로 <b>계산</b>한다(소득구간 기본금리 − 지방 조정 − 우대, 상한 적용).
 * {@link #isComputed()} 가 어느 모드인지 가른다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RateSpec(
        @JsonProperty("min_fact_code") String minFactCode,
        @JsonProperty("max_fact_code") String maxFactCode,
        @JsonProperty("income_bands") List<IncomeBand> incomeBands,
        @JsonProperty("regional_discount_fact_code") String regionalDiscountFactCode,
        @JsonProperty("sme_preference_fact_code") String smePreferenceFactCode,
        @JsonProperty("preference_cap_fact_code") String preferenceCapFactCode) {

    /** 범위 모드용 축약 생성자. 기존 {@code new RateSpec(min, max)} 호출을 그대로 유지한다. */
    public RateSpec(String minFactCode, String maxFactCode) {
        this(minFactCode, maxFactCode, null, null, null, null);
    }

    /** 소득구간표가 있으면 계산 모드다. 없으면 min/max 범위 모드로 동작한다. */
    public boolean isComputed() {
        return incomeBands != null && !incomeBands.isEmpty();
    }

    /**
     * 소득구간 한 칸. 연소득이 {@code ceilingFactCode} 이하이면 {@code rateFactCode} 를 기본금리로
     * 쓴다. 목록 순서대로(낮은 구간부터) 처음 맞는 칸을 고른다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncomeBand(
            @JsonProperty("ceiling_fact_code") String ceilingFactCode,
            @JsonProperty("rate_fact_code") String rateFactCode) {}
}
