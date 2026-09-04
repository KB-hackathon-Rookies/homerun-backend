package com.homerun.domain.policy.type;

/**
 * {@code policy_rule.status}. DRAFT 초안 → REVIEWED 검수완료 → ACTIVE 적용.
 *
 * <p>ACTIVE 만 판정에 쓴다. 사람이 조건식을 검수해 올리기 전까지 룰엔진은 그 정책을
 * NEED_INFO 로 둔다 — AI가 초안을 만들 수는 있어도 최종 확정은 사람의 몫이다.
 */
public enum PolicyRuleStatus {
    DRAFT,
    REVIEWED,
    ACTIVE,
    RETIRED
}
