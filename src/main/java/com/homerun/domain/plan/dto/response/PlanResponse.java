package com.homerun.domain.plan.dto.response;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.plan.type.PlanStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PlanResponse(
        Long id,
        LeaseType leaseType,
        PlanStage stage,
        PlanStage lastVisitedStage,
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
                plan.getLastVisitedStage(),
                plan.getStatus(),
                plan.getLastLocationCode(),
                plan.getRuleVersion(),
                plan.getTargetMoveDate(),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                steps.stream().map(PlanStepResponse::from).toList());
    }
}
