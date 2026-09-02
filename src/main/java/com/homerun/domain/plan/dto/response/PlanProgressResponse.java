package com.homerun.domain.plan.dto.response;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.plan.type.PlanStatus;
import com.homerun.domain.plan.type.PlanStepStatus;
import java.util.List;

public record PlanProgressResponse(
        Long planId,
        PlanStage currentStage,
        PlanStage lastVisitedStage,
        PlanStatus planStatus,
        String lastLocationCode,
        int completedSteps,
        int totalSteps,
        int progressPercent,
        List<PlanStepResponse> steps) {

    public static PlanProgressResponse from(Plan plan, List<PlanStep> steps) {
        int completed = (int) steps.stream()
                .filter(step -> step.getStatus() == PlanStepStatus.DONE || step.getStatus() == PlanStepStatus.SKIPPED)
                .count();
        int percent = steps.isEmpty() ? 0 : completed * 100 / steps.size();
        return new PlanProgressResponse(
                plan.getId(),
                plan.getStage(),
                plan.getLastVisitedStage(),
                plan.getStatus(),
                plan.getLastLocationCode(),
                completed,
                steps.size(),
                percent,
                steps.stream().map(PlanStepResponse::from).toList());
    }
}
