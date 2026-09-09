package com.homerun.domain.plan.dto.response;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.type.PlanGate;
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

    /**
     * 관문 하나가 이미 통과됐는지.
     *
     * <p>같은 회차를 다시 받은 요청(재생)이 관문을 마저 열어야 하는지 판단할 때 쓴다. 되감기
     * 뒤에는 제출 기록이 남은 채 관문만 되돌아가 있어서, 제출 기록만으로는 알 수 없다.
     */
    public boolean isGateCompleted(PlanGate gate) {
        return steps.stream().anyMatch(step -> step.code().equals(gate.code()) && step.status() == PlanStepStatus.DONE);
    }

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
