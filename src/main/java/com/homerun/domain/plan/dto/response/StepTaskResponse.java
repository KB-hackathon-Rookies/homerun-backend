package com.homerun.domain.plan.dto.response;

import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.entity.StepTask;
import com.homerun.domain.plan.type.StepTaskStatus;
import com.homerun.domain.plan.type.TaskRecurrence;
import java.time.Instant;
import java.time.LocalDate;

public record StepTaskResponse(
        Long id,
        String stepCode,
        String taskCode,
        String taskName,
        int sequence,
        StepTaskStatus status,
        boolean irreversible,
        boolean required,
        boolean skippable,
        LocalDate dueAt,
        TaskRecurrence recurrence,
        Instant completedAt) {

    public static StepTaskResponse from(StepTask task, PlanStep step, boolean skippable) {
        return new StepTaskResponse(
                task.getId(),
                step.getStepCode(),
                task.getTaskCode(),
                task.getTaskName(),
                task.getSequence(),
                task.getStatus(),
                task.isIrreversible(),
                !skippable,
                skippable,
                task.getDueAt(),
                task.getRecurrence(),
                task.getCompletedAt());
    }
}
