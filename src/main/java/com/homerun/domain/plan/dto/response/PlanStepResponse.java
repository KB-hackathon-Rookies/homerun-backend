package com.homerun.domain.plan.dto.response;

import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.type.PlanStepStatus;
import java.time.Instant;
import java.util.List;

public record PlanStepResponse(
        String code,
        String name,
        int sequence,
        PlanStepStatus status,
        List<String> dependsOn,
        boolean irreversible,
        Instant completedAt) {

    public static PlanStepResponse from(PlanStep step) {
        return new PlanStepResponse(
                step.getStepCode(),
                step.getStepName(),
                step.getSequence(),
                step.getStatus(),
                step.getDependsOn(),
                step.isIrreversible(),
                step.getCompletedAt());
    }
}
