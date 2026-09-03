package com.homerun.domain.plan.service;

import com.homerun.domain.plan.dto.request.UpdateStepTaskStatusRequest;
import com.homerun.domain.plan.dto.response.PlanTaskProgressResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.entity.StepTask;
import com.homerun.domain.plan.policy.PlanStageTransitionPolicy;
import com.homerun.domain.plan.policy.StepTaskSkipPolicy;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.repository.StepTaskRepository;
import com.homerun.domain.plan.type.PlanStepStatus;
import com.homerun.domain.plan.type.StepTaskStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanTaskService {

    private final PlanRepository planRepository;
    private final PlanStepRepository planStepRepository;
    private final StepTaskRepository stepTaskRepository;
    private final PlanStageTransitionPolicy transitionPolicy;
    private final StepTaskSkipPolicy skipPolicy;

    public PlanTaskService(
            PlanRepository planRepository,
            PlanStepRepository planStepRepository,
            StepTaskRepository stepTaskRepository,
            PlanStageTransitionPolicy transitionPolicy,
            StepTaskSkipPolicy skipPolicy) {
        this.planRepository = planRepository;
        this.planStepRepository = planStepRepository;
        this.stepTaskRepository = stepTaskRepository;
        this.transitionPolicy = transitionPolicy;
        this.skipPolicy = skipPolicy;
    }

    @Transactional(readOnly = true)
    public PlanTaskProgressResponse getTasks(Long memberId, Long planId) {
        Plan plan = findOwnedPlan(memberId, planId);
        return response(plan, findSteps(planId), findTasks(planId));
    }

    @Transactional
    public PlanTaskProgressResponse updateStatus(
            Long memberId, Long planId, String taskCode, UpdateStepTaskStatusRequest request) {
        Plan plan = findOwnedPlan(memberId, planId);
        plan.verifyRuleVersion(request.ruleVersion());
        List<PlanStep> steps = findSteps(planId);
        List<StepTask> tasks = findTasks(planId);
        StepTask task = tasks.stream()
                .filter(candidate -> candidate.getTaskCode().equals(taskCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_TASK_NOT_FOUND));
        PlanStep step = steps.stream()
                .filter(candidate -> candidate.getId().equals(task.getPlanStepId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_STEP_NOT_FOUND));

        if (task.getStatus() == request.status()) {
            return response(plan, steps, tasks);
        }

        validateTransition(task, request.status());
        step.start();
        applyStatus(task, request.status());

        boolean allTasksSettled = tasks.stream()
                .filter(candidate -> candidate.getPlanStepId().equals(step.getId()))
                .allMatch(StepTask::isSettled);
        if (allTasksSettled && step.complete()) {
            transitionPolicy.applyCompletedGate(plan, step);
            List<String> completedCodes = steps.stream()
                    .filter(candidate -> candidate.getStatus() == PlanStepStatus.DONE)
                    .map(PlanStep::getStepCode)
                    .toList();
            steps.forEach(candidate -> candidate.unlockWhenDependenciesCompleted(completedCodes));
        }
        return response(plan, steps, tasks);
    }

    private void validateTransition(StepTask task, StepTaskStatus target) {
        if (target != StepTaskStatus.DOING && target != StepTaskStatus.DONE && target != StepTaskStatus.SKIPPED) {
            throw new BusinessException(ErrorCode.INVALID_TASK_STATUS_TRANSITION);
        }
        if (task.isSettled()) {
            throw new BusinessException(ErrorCode.INVALID_TASK_STATUS_TRANSITION);
        }
        if (target == StepTaskStatus.SKIPPED && !skipPolicy.isSkippable(task.getTaskCode())) {
            throw new BusinessException(ErrorCode.PLAN_TASK_SKIP_NOT_ALLOWED);
        }
    }

    private void applyStatus(StepTask task, StepTaskStatus target) {
        switch (target) {
            case DOING -> task.start();
            case DONE -> task.complete();
            case SKIPPED -> task.skip();
            default -> throw new BusinessException(ErrorCode.INVALID_TASK_STATUS_TRANSITION);
        }
    }

    private Plan findOwnedPlan(Long memberId, Long planId) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return plan;
    }

    private List<PlanStep> findSteps(Long planId) {
        return planStepRepository.findAllByPlanIdOrderBySequenceAsc(planId);
    }

    private List<StepTask> findTasks(Long planId) {
        return stepTaskRepository.findAllByPlanId(planId);
    }

    private PlanTaskProgressResponse response(Plan plan, List<PlanStep> steps, List<StepTask> tasks) {
        return PlanTaskProgressResponse.from(plan, steps, tasks, skipPolicy);
    }
}
