package com.homerun.domain.policy.repository;

import com.homerun.domain.policy.entity.PolicyRule;
import com.homerun.domain.policy.type.PolicyRuleStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRuleRepository extends JpaRepository<PolicyRule, Long> {

    /** ACTIVE 만 판정에 쓴다는 스키마 규칙 — 여러 버전이 있어도 최신 버전 하나만 고른다. */
    Optional<PolicyRule> findFirstByPolicyIdAndStatusOrderByVersionDesc(Long policyId, PolicyRuleStatus status);
}
