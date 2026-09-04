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
import com.homerun.domain.plan.entity.StepTask;
import com.homerun.domain.plan.policy.StepTaskSkipPolicy;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.repository.StepTaskRepository;
import com.homerun.domain.plan.type.PlanStepStatus;
import com.homerun.domain.plan.type.StepTaskStatus;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드의 할 일 목록은 관문(plan_step)이 아니라 할 일(step_task)을 읽는다.
 *
 * <p>"3루 계약·신청 실행"은 사용자에게 줄 수 있는 지시가 아니다. "전입신고 (D-3)" 여야
 * 행동으로 옮길 수 있다. 마감과 비가역 표시도 같은 이유로 할 일에 붙어 있다.
 */
@Service
public class DashboardService {

    /** 관문이 아직 잠겨 있으면 그 안의 할 일도 아직 할 수 없다. */
    private static final Set<PlanStepStatus> OPEN_STEP_STATUSES =
            EnumSet.of(PlanStepStatus.READY, PlanStepStatus.DOING, PlanStepStatus.RECALC_REQUIRED);

    private static final Comparator<DashboardTaskResponse> TASK_PRIORITY = DashboardService::compareTaskPriority;

    private static final Comparator<Deadline> DEADLINE_PRIORITY = Comparator.comparing(Deadline::getDueDate)
            .thenComparing(deadline -> !deadline.isAbsolute())
            .thenComparingInt(deadline -> deadlineTypePriority(deadline.getType()))
            .thenComparing(Deadline::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final PlanRepository planRepository;
    private final PlanStepRepository planStepRepository;
    private final StepTaskRepository stepTaskRepository;
    private final DeadlineRepository deadlineRepository;
    private final Clock clock;
    private final StepTaskSkipPolicy skipPolicy;

    public DashboardService(
            PlanRepository planRepository,
            PlanStepRepository planStepRepository,
            StepTaskRepository stepTaskRepository,
            DeadlineRepository deadlineRepository,
            Clock clock,
            StepTaskSkipPolicy skipPolicy) {
        this.planRepository = planRepository;
        this.planStepRepository = planStepRepository;
        this.stepTaskRepository = stepTaskRepository;
        this.deadlineRepository = deadlineRepository;
        this.clock = clock;
        this.skipPolicy = skipPolicy;
    }

    @Transactional(readOnly = true)
    public DashboardResponse get(Long memberId, Long planId) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);

        List<PlanStep> steps = planStepRepository.findAllByPlanIdOrderBySequenceAsc(planId);
        Map<Long, PlanStep> stepsById = steps.stream().collect(Collectors.toMap(PlanStep::getId, Function.identity()));
        List<StepTask> tasks = stepTaskRepository.findAllByPlanId(planId);

        List<StepTask> required = tasks.stream()
                .filter(task -> skipPolicy.isRequired(task.getTaskCode()))
                .toList();
        int completedTasks = (int) required.stream()
                .filter(task -> task.getStatus() == StepTaskStatus.DONE)
                .count();
        int totalTasks = required.size();
        int progressPercent = totalTasks == 0 ? 0 : completedTasks * 100 / totalTasks;

        Map<Long, Deadline> nearestDeadlines = nearestDeadlines(planId);
        LocalDate today = LocalDate.now(clock);

        List<DashboardTaskResponse> prioritizedTasks = tasks.stream()
                .filter(StepTask::isActionable)
                .filter(task -> isOpen(stepsById.get(task.getPlanStepId())))
                .map(task -> DashboardTaskResponse.from(
                        stepCodeOf(stepsById.get(task.getPlanStepId())),
                        task,
                        nearestDeadlines.get(task.getId()),
                        today))
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
                new DashboardProgressResponse(completedTasks, totalTasks, progressPercent),
                prioritizedTasks);
    }

    private static boolean isOpen(PlanStep step) {
        return step != null && OPEN_STEP_STATUSES.contains(step.getStatus());
    }

    private static String stepCodeOf(PlanStep step) {
        return step == null ? null : step.getStepCode();
    }

    private Map<Long, Deadline> nearestDeadlines(Long planId) {
        Map<Long, Deadline> deadlinesByTask = new HashMap<>();
        deadlineRepository.findAllByPlanIdAndTaskIdIsNotNull(planId).stream()
                .filter(deadline -> deadline.getDueDate() != null)
                .forEach(deadline ->
                        deadlinesByTask.merge(deadline.getTaskId(), deadline, DashboardService::earlierDeadline));
        return deadlinesByTask;
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

    private static int statusPriority(StepTaskStatus status) {
        return switch (status) {
            case RECALC_REQUIRED -> 0;
            case DOING -> 1;
            case TODO -> 2;
            default -> Integer.MAX_VALUE;
        };
    }
}
