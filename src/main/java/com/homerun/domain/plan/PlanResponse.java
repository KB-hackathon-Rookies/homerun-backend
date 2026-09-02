package com.homerun.domain.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PlanResponse(
        Long id,
        LeaseType leaseType,
        PlanStage stage,
        PlanStatus status,
        String lastLocationCode,
        String ruleVersion,
        LocalDate targetMoveDate,
        Instant createdAt,
        Instant updatedAt,
        List<PlanStepResponse> steps) {

    public static PlanResponse from(Plan plan, List<PlanStep> steps) {
        return new PlanResponse(
                plan.getId(),
                plan.getLeaseType(),
                plan.getStage(),
                plan.getStatus(),
                plan.getLastLocationCode(),
                plan.getRuleVersion(),
                plan.getTargetMoveDate(),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                steps.stream().map(PlanStepResponse::from).toList());
    }
}
