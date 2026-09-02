package com.homerun.domain.dashboard.service;

import com.homerun.domain.dashboard.dto.response.DashboardProgressResponse;
import com.homerun.domain.dashboard.dto.response.DashboardResponse;
import com.homerun.domain.dashboard.dto.response.DashboardResumeResponse;
import com.homerun.domain.dashboard.dto.response.DashboardTaskResponse;
import com.homerun.domain.dashboard.entity.Deadline;
import com.homerun.domain.dashboard.repository.DeadlineRepository;
import com.homerun.domain.dashboard.type.DeadlineType;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.type.PlanStepStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final Set<PlanStepStatus> ACTIONABLE_STATUSES =
            EnumSet.of(PlanStepStatus.RECALC_REQUIRED, PlanStepStatus.DOING, PlanStepStatus.READY);

    private static final Comparator<DashboardTaskResponse> TASK_PRIORITY = DashboardService::compareTaskPriority;

    private static final Comparator<Deadline> DEADLINE_PRIORITY = Comparator.comparing(Deadline::getDueDate)
            .thenComparing(deadline -> !deadline.isAbsolute())
            .thenComparingInt(deadline -> deadlineTypePriority(deadline.getType()))
            .thenComparing(Deadline::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final PlanRepository planRepository;
    private final PlanStepRepository planStepRepository;
    private final DeadlineRepository deadlineRepository;
    private final Clock clock;

    public DashboardService(
            PlanRepository planRepository,
            PlanStepRepository planStepRepository,
            DeadlineRepository deadlineRepository,
            Clock clock) {
        this.planRepository = planRepository;
        this.planStepRepository = planStepRepository;
        this.deadlineRepository = deadlineRepository;
        this.clock = clock;
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
        Map<Long, Deadline> nearestDeadlines = nearestDeadlines(planId);
        LocalDate today = LocalDate.now(clock);

        List<DashboardTaskResponse> prioritizedTasks = steps.stream()
                .filter(step -> ACTIONABLE_STATUSES.contains(step.getStatus()))
                .map(step -> DashboardTaskResponse.from(step, nearestDeadlines.get(step.getId()), today))
                .sorted(TASK_PRIORITY)
                .toList();

        return new DashboardResponse(
                plan.getId(),
                plan.getLeaseType(),
                plan.getStage(),
                plan.getStatus(),
                plan.getLastLocationCode(),
                DashboardResumeResponse.from(plan),
                plan.getTargetMoveDate(),
                new DashboardProgressResponse(completedSteps, totalSteps, progressPercent),
                prioritizedTasks);
    }

    private Map<Long, Deadline> nearestDeadlines(Long planId) {
        Map<Long, Deadline> deadlinesByStep = new HashMap<>();
        deadlineRepository.findAllByPlanIdAndStepIdIsNotNull(planId).stream()
                .filter(deadline -> deadline.getDueDate() != null)
                .forEach(deadline ->
                        deadlinesByStep.merge(deadline.getStepId(), deadline, DashboardService::earlierDeadline));
        return deadlinesByStep;
    }

    private static Deadline earlierDeadline(Deadline left, Deadline right) {
        return DEADLINE_PRIORITY.compare(left, right) <= 0 ? left : right;
    }

    private static int compareTaskPriority(DashboardTaskResponse left, DashboardTaskResponse right) {
        boolean leftHasDeadline = left.dueDate() != null;
        boolean rightHasDeadline = right.dueDate() != null;
        if (leftHasDeadline != rightHasDeadline) {
            return leftHasDeadline ? -1 : 1;
        }
        if (leftHasDeadline) {
            int dateComparison = left.dueDate().compareTo(right.dueDate());
            if (dateComparison != 0) {
                return dateComparison;
            }
            int irreversibleComparison = Boolean.compare(!left.irreversible(), !right.irreversible());
            if (irreversibleComparison != 0) {
                return irreversibleComparison;
            }
        }
        int statusComparison = Integer.compare(statusPriority(left.status()), statusPriority(right.status()));
        return statusComparison != 0 ? statusComparison : Integer.compare(left.sequence(), right.sequence());
    }

    private static int deadlineTypePriority(DeadlineType type) {
        return switch (type) {
            case LEGAL -> 0;
            case RECOMMENDED -> 1;
            case PROCESSING -> 2;
        };
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
