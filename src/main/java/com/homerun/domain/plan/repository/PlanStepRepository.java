package com.homerun.domain.plan.repository;

import com.homerun.domain.plan.entity.PlanStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanStepRepository
        extends JpaRepository<PlanStep, Long> {

    List<PlanStep> findAllByPlanIdOrderByStepGroup(Long planId);

    Optional<PlanStep> findByPlanIdAndStepCode(
            Long planId,
            String stepCode
    );

    List<PlanStep> findAllByPlanIdOrderBySequenceAsc(Long planId);
}