package com.homerun.domain.policy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * {@code policy_rule.rule_json} 전체.
 *
 * <p>v1 은 {@code operator: "AND"} 만 지원한다 — 조건 하나라도 FAIL 이면 FAIL, 전부 확인됐으면
 * PASS, 그 사이 확인 못 한 게 있으면 NEED_INFO. {@code amount}/{@code rate} 는 예상 금액·금리
 * 계산용으로 시드에 남겨는 뒀지만 v1 판정 로직은 안 읽는다 — 그 계산은 #80 하드코딩 서비스와의
 * 관계가 정리된 다음 이슈다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleDocument(String operator, List<RuleCondition> conditions) {}
