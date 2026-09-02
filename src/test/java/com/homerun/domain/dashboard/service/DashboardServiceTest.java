package com.homerun.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.homerun.domain.dashboard.dto.response.DashboardResponse;
import com.homerun.domain.dashboard.entity.Deadline;
import com.homerun.domain.dashboard.repository.DeadlineRepository;
import com.homerun.domain.dashboard.type.DashboardTaskPriorityReason;
import com.homerun.domain.dashboard.type.DeadlineType;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.plan.type.PlanStepStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 2);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PlanRepository planRepository;

    @Mock
    private PlanStepRepository planStepRepository;

    @Mock
    private DeadlineRepository deadlineRepository;

    private DashboardService dashboardService;
    private Plan plan;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(planRepository, planStepRepository, deadlineRepository, CLOCK);
        plan = Plan.create(MEMBER_ID, LeaseType.WOLSE, LocalDate.of(2027, 2, 1));
        ReflectionTestUtils.setField(plan, "id", PLAN_ID);
    }

    @Test
    void should_countDoneAndSkippedSteps_when_calculatingProgress() {
        givenPlanAndSteps(List.of(
                step("DONE", PlanStepStatus.DONE, 1),
                step("SKIPPED", PlanStepStatus.SKIPPED, 2),
                step("READY", PlanStepStatus.READY, 3)));

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.progress().completedSteps()).isEqualTo(2);
        assertThat(response.progress().totalSteps()).isEqualTo(3);
        assertThat(response.progress().progressPercent()).isEqualTo(66);
    }

    @Test
    void should_returnZeroProgress_when_planHasNoSteps() {
        givenPlanAndSteps(List.of());

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.progress().completedSteps()).isZero();
        assertThat(response.progress().totalSteps()).isZero();
        assertThat(response.progress().progressPercent()).isZero();
    }

    @Test
    void should_prioritizeRecalculationThenDoingThenReady_when_tasksHaveDifferentStatuses() {
        givenPlanAndSteps(List.of(
                step("READY", PlanStepStatus.READY, 1),
                step("RECALC", PlanStepStatus.RECALC_REQUIRED, 3),
                step("DOING", PlanStepStatus.DOING, 2)));

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks())
                .extracting(task -> task.status())
                .containsExactly(PlanStepStatus.RECALC_REQUIRED, PlanStepStatus.DOING, PlanStepStatus.READY);
    }

    @Test
    void should_orderBySequence_when_tasksHaveSameStatus() {
        givenPlanAndSteps(
                List.of(step("READY_FOUR", PlanStepStatus.READY, 4), step("READY_TWO", PlanStepStatus.READY, 2)));

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks())
                .extracting(task -> task.sequence())
                .containsExactly(2, 4);
    }

    @Test
    void should_prioritizeEarlierDeadline_beforeStatusPriority() {
        PlanStep readySoon = step("READY_SOON", PlanStepStatus.READY, 1);
        PlanStep recalcLater = step("RECALC_LATER", PlanStepStatus.RECALC_REQUIRED, 2);
        givenPlanAndSteps(List.of(recalcLater, readySoon));
        List<Deadline> deadlines = List.of(
                deadline(recalcLater, "재계산 마감", TODAY.plusDays(5), false),
                deadline(readySoon, "신청 마감", TODAY.plusDays(1), false));
        when(deadlineRepository.findAllByPlanIdAndStepIdIsNotNull(PLAN_ID)).thenReturn(deadlines);

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks())
                .extracting(task -> task.stepCode())
                .containsExactly("READY_SOON", "RECALC_LATER");
    }

    @Test
    void should_prioritizeIrreversibleTask_when_dueDatesAreSame() {
        PlanStep reversible = step("REVERSIBLE", PlanStepStatus.READY, 1);
        PlanStep irreversible = step("IRREVERSIBLE", PlanStepStatus.READY, 2);
        ReflectionTestUtils.setField(irreversible, "irreversible", true);
        givenPlanAndSteps(List.of(reversible, irreversible));
        List<Deadline> deadlines = List.of(
                deadline(reversible, "서류 확인", TODAY.plusDays(3), false),
                deadline(irreversible, "계약 체결", TODAY.plusDays(3), true));
        when(deadlineRepository.findAllByPlanIdAndStepIdIsNotNull(PLAN_ID)).thenReturn(deadlines);

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks())
                .extracting(task -> task.stepCode())
                .containsExactly("IRREVERSIBLE", "REVERSIBLE");
        assertThat(response.prioritizedTasks().get(0).priorityReason())
                .isEqualTo(DashboardTaskPriorityReason.IRREVERSIBLE_DEADLINE);
    }

    @Test
    void should_useNearestDeadlineAndReturnNegativeDays_when_deadlineIsOverdue() {
        PlanStep ready = step("READY", PlanStepStatus.READY, 1);
        givenPlanAndSteps(List.of(ready));
        List<Deadline> deadlines = List.of(
                deadline(ready, "권장 마감", TODAY.plusDays(7), false), deadline(ready, "법정 마감", TODAY.minusDays(2), true));
        when(deadlineRepository.findAllByPlanIdAndStepIdIsNotNull(PLAN_ID)).thenReturn(deadlines);

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks().get(0)).satisfies(task -> {
            assertThat(task.deadlineLabel()).isEqualTo("법정 마감");
            assertThat(task.deadlineType()).isEqualTo(DeadlineType.LEGAL);
            assertThat(task.dueDate()).isEqualTo(TODAY.minusDays(2));
            assertThat(task.daysUntilDue()).isEqualTo(-2);
            assertThat(task.priorityReason()).isEqualTo(DashboardTaskPriorityReason.OVERDUE);
        });
    }

    @Test
    void should_returnLastVisitedStageAndLocation_when_resumingPlan() {
        ReflectionTestUtils.setField(plan, "lastVisitedStage", PlanStage.SECOND);
        ReflectionTestUtils.setField(plan, "lastLocationCode", "POLICY_MATCHING");
        givenPlanAndSteps(List.of());

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.resume().stage()).isEqualTo(PlanStage.SECOND);
        assertThat(response.resume().locationCode()).isEqualTo("POLICY_MATCHING");
    }

    @Test
    void should_excludeCompletedSkippedAndLockedSteps_when_buildingTasks() {
        givenPlanAndSteps(List.of(
                step("DONE", PlanStepStatus.DONE, 1),
                step("SKIPPED", PlanStepStatus.SKIPPED, 2),
                step("LOCKED", PlanStepStatus.LOCKED, 3),
                step("READY", PlanStepStatus.READY, 4),
                step("DOING", PlanStepStatus.DOING, 5),
                step("RECALC", PlanStepStatus.RECALC_REQUIRED, 6)));

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks())
                .extracting(task -> task.stepCode())
                .containsExactly("RECALC", "DOING", "READY");
    }

    @Test
    void should_throwPlanNotFound_when_planDoesNotExist() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.get(MEMBER_ID, PLAN_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        verify(planStepRepository, never()).findAllByPlanIdOrderBySequenceAsc(PLAN_ID);
    }

    @Test
    void should_throwPlanAccessDenied_when_planBelongsToAnotherMember() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> dashboardService.get(999L, PLAN_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_ACCESS_DENIED));
        verify(planStepRepository, never()).findAllByPlanIdOrderBySequenceAsc(PLAN_ID);
    }

    private void givenPlanAndSteps(List<PlanStep> steps) {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(planStepRepository.findAllByPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(steps);
    }

    private PlanStep step(String code, PlanStepStatus status, int sequence) {
        PlanStep step = PlanStep.defaultSteps(PLAN_ID).get(0);
        ReflectionTestUtils.setField(step, "stepCode", code);
        ReflectionTestUtils.setField(step, "stepName", code);
        ReflectionTestUtils.setField(step, "status", status);
        ReflectionTestUtils.setField(step, "sequence", sequence);
        ReflectionTestUtils.setField(step, "id", (long) sequence);
        return step;
    }

    private Deadline deadline(PlanStep step, String label, LocalDate dueDate, boolean absolute) {
        Deadline deadline = mock(Deadline.class, withSettings().lenient());
        when(deadline.getStepId()).thenReturn(step.getId());
        when(deadline.getType()).thenReturn(absolute ? DeadlineType.LEGAL : DeadlineType.RECOMMENDED);
        when(deadline.getLabel()).thenReturn(label);
        when(deadline.getDueDate()).thenReturn(dueDate);
        when(deadline.isAbsolute()).thenReturn(absolute);
        return deadline;
    }
}
