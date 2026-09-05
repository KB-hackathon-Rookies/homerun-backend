package com.homerun.domain.policy.repository;

import com.homerun.domain.policy.entity.PolicyRule;
import com.homerun.domain.policy.type.PolicyRuleStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRuleRepository extends JpaRepository<PolicyRule, Long> {

    /** ACTIVE 만 판정에 쓴다는 스키마 규칙 — 여러 버전이 있어도 최신 버전 하나만 고른다. */
    Optional<PolicyRule> findFirstByPolicyIdAndStatusOrderByVersionDesc(Long policyId, PolicyRuleStatus status);

    /** 정책 전체를 훑어야 하는 경우(ALT-01-04 재도전 큐)에 쓴다. 정책 하나에 여러 버전이 ACTIVE로
     * 잡혀 있을 수 있어(테스트 등) 호출한 쪽에서 정책별 최신 버전만 다시 골라야 한다. */
    List<PolicyRule> findAllByStatus(PolicyRuleStatus status);
}
