package com.homerun.domain.plan.repository;

import com.homerun.domain.plan.entity.PlanStep;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanStepRepository extends JpaRepository<PlanStep, Long> {
    List<PlanStep> findAllByPlanIdOrderBySequenceAsc(Long planId);

    Optional<PlanStep> findByPlanIdAndStepCode(Long planId, String stepCode);
}
