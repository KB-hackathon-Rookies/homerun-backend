package com.homerun.domain.policy.repository;

import com.homerun.domain.policy.entity.PolicyVerdict;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyVerdictRepository extends JpaRepository<PolicyVerdict, Long> {
    Optional<PolicyVerdict> findByPlanIdAndPolicyIdAndRuleId(Long planId, Long policyId, Long ruleId);

    List<PolicyVerdict> findAllByPlanId(Long planId);

    List<PolicyVerdict> findAllByPropertyId(Long propertyId);
}
