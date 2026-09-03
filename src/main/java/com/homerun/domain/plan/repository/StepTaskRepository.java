package com.homerun.domain.plan.repository;

import com.homerun.domain.plan.entity.StepTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StepTaskRepository
        extends JpaRepository<StepTask, Long> {

    List<StepTask> findAllByPlanStepIdOrderBySequence(Long planStepId);

    Optional<StepTask> findByPlanStepIdAndTaskCode(
            Long planStepId,
            String taskCode
    );
}