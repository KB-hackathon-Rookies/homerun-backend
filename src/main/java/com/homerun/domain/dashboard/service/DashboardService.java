package com.homerun.domain.dashboard.service;

import com.homerun.domain.dashboard.dto.response.DashboardProgressResponse;
import com.homerun.domain.dashboard.dto.response.DashboardResponse;
import com.homerun.domain.dashboard.dto.response.DashboardTaskResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.type.PlanStepStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final Set<PlanStepStatus> ACTIONABLE_STATUSES =
            EnumSet.of(PlanStepStatus.RECALC_REQUIRED, PlanStepStatus.DOING, PlanStepStatus.READY);

    private static final Comparator<PlanStep> TASK_PRIORITY = Comparator.comparingInt(
                    (PlanStep step) -> statusPriority(step.getStatus()))
            .thenComparingInt(PlanStep::getSequence);

    private final PlanRepository planRepository;
    private final PlanStepRepository planStepRepository;

    public DashboardService(PlanRepository planRepository, PlanStepRepository planStepRepository) {
        this.planRepository = planRepository;
        this.planStepRepository = planStepRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse get(Long memberId, Long planId) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);

        List<PlanStep> steps = planStepRepository.findAllByPlanIdOrderBySequenceAsc(planId);
        int completedSteps = (int) steps.stream()
                .filter(step -> step.getStatus() == PlanStepStatus.DONE || step.getStatus() == PlanStepStatus.SKIPPED)
                .count();
        int totalSteps = steps.size();
        int progressPercent = totalSteps == 0 ? 0 : completedSteps * 100 / totalSteps;

        List<DashboardTaskResponse> prioritizedTasks = steps.stream()
                .filter(step -> ACTIONABLE_STATUSES.contains(step.getStatus()))
                .sorted(TASK_PRIORITY)
                .map(DashboardTaskResponse::from)
                .toList();

        return new DashboardResponse(
                plan.getId(),
                plan.getLeaseType(),
                plan.getStage(),
                plan.getStatus(),
                plan.getLastLocationCode(),
                plan.getTargetMoveDate(),
                new DashboardProgressResponse(completedSteps, totalSteps, progressPercent),
                prioritizedTasks);
    }

    private static int statusPriority(PlanStepStatus status) {
        return switch (status) {
            case RECALC_REQUIRED -> 0;
            case DOING -> 1;
            case READY -> 2;
            default -> Integer.MAX_VALUE;
        };
    }
}
