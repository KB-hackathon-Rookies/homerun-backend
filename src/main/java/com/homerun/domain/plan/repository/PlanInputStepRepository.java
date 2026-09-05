package com.homerun.domain.plan.repository;

import com.homerun.domain.plan.entity.PlanInputStep;
import com.homerun.domain.plan.type.DiagnosisInputStep;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanInputStepRepository extends JpaRepository<PlanInputStep, Long> {
    Optional<PlanInputStep> findByPlanIdAndStepCode(Long planId, DiagnosisInputStep stepCode);

    List<PlanInputStep> findAllByPlanId(Long planId);
}
