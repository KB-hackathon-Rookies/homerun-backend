package com.homerun.domain.application;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyApplicationRepository extends JpaRepository<PolicyApplication, Long> {

    List<PolicyApplication> findByPlanIdOrderByIdDesc(Long planId);

    boolean existsByPlanIdAndPolicyId(Long planId, Long policyId);
}
