package com.homerun.domain.plan.dto.response;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.entity.StepTask;
import com.homerun.domain.plan.policy.StepTaskSkipPolicy;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.plan.type.PlanStatus;
import com.homerun.domain.plan.type.StepTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record PlanTaskProgressResponse(
        Long planId,
        PlanStage currentStage,
        PlanStatus planStatus,
        @Schema(description = "DONE 상태인 필수 작업 수") int completedTasks,
        @Schema(description = "필수 작업 수 (선택 작업 제외)") int totalTasks,
        int progressPercent,
        List<StepTaskResponse> tasks) {

    public static PlanTaskProgressResponse from(
            Plan plan, List<PlanStep> steps, List<StepTask> tasks, StepTaskSkipPolicy skipPolicy) {
        Map<Long, PlanStep> stepById = steps.stream().collect(Collectors.toMap(PlanStep::getId, Function.identity()));
        List<StepTask> required = tasks.stream()
                .filter(task -> skipPolicy.isRequired(task.getTaskCode()))
                .toList();
        int completed = (int) required.stream()
                .filter(task -> task.getStatus() == StepTaskStatus.DONE)
                .count();
        int percent = required.isEmpty() ? 0 : completed * 100 / required.size();
        List<StepTaskResponse> responses = tasks.stream()
                .sorted(Comparator.comparingInt((StepTask task) ->
                                stepById.get(task.getPlanStepId()).getSequence())
                        .thenComparingInt(StepTask::getSequence))
                .map(task -> StepTaskResponse.from(
                        task, stepById.get(task.getPlanStepId()), skipPolicy.isSkippable(task.getTaskCode())))
                .toList();
        return new PlanTaskProgressResponse(
                plan.getId(), plan.getStage(), plan.getStatus(), completed, required.size(), percent, responses);
    }
}
