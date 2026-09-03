package com.homerun.domain.plan.dto.response;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.entity.StepTask;
import com.homerun.domain.plan.policy.StepTaskSkipPolicy;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.plan.type.PlanStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record PlanTaskProgressResponse(
        Long planId,
        PlanStage currentStage,
        PlanStatus planStatus,
        int completedTasks,
        int totalTasks,
        int progressPercent,
        List<StepTaskResponse> tasks) {

    public static PlanTaskProgressResponse from(
            Plan plan, List<PlanStep> steps, List<StepTask> tasks, StepTaskSkipPolicy skipPolicy) {
        Map<Long, PlanStep> stepById = steps.stream().collect(Collectors.toMap(PlanStep::getId, Function.identity()));
        int completed = (int) tasks.stream().filter(StepTask::isSettled).count();
        int percent = tasks.isEmpty() ? 0 : completed * 100 / tasks.size();
        List<StepTaskResponse> responses = tasks.stream()
                .sorted(Comparator.comparingInt((StepTask task) ->
                                stepById.get(task.getPlanStepId()).getSequence())
                        .thenComparingInt(StepTask::getSequence))
                .map(task -> StepTaskResponse.from(
                        task, stepById.get(task.getPlanStepId()), skipPolicy.isSkippable(task.getTaskCode())))
                .toList();
        return new PlanTaskProgressResponse(
                plan.getId(), plan.getStage(), plan.getStatus(), completed, tasks.size(), percent, responses);
    }
}
