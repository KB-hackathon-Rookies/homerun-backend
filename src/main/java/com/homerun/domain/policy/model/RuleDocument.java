package com.homerun.domain.policy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * {@code policy_rule.rule_json} 전체.
 *
 * <p>v1 은 {@code operator: "AND"} 만 지원한다 — 조건 하나라도 FAIL 이면 FAIL, 전부 확인됐으면
 * PASS, 그 사이 확인 못 한 게 있으면 NEED_INFO. {@code amount}/{@code rate} 는 조건 판정과
 * 별개로 예상 대출액·금리를 계산할 때만 쓴다({@link com.homerun.domain.policy.service.PolicyRuleEngine#estimate}) —
 * 없는 정책(반환보증 등)은 null 이라 예상치를 안 준다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleDocument(String operator, List<RuleCondition> conditions, AmountSpec amount, RateSpec rate) {

    /** amount/rate 스펙이 없는 정책(반환보증 등)이나 테스트 픽스처용 편의 생성자. */
    public RuleDocument(String operator, List<RuleCondition> conditions) {
        this(operator, conditions, null, null);
    }
}
