package com.homerun.domain.plan;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanInputRepository extends JpaRepository<PlanInput, Long> {
    Optional<PlanInput> findByPlanId(Long planId);
}
