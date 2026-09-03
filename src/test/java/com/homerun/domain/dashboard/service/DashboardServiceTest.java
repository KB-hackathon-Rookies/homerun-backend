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
import com.homerun.domain.plan.entity.StepTask;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.repository.StepTaskRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.plan.type.PlanStepStatus;
import com.homerun.domain.plan.type.StepTaskStatus;
import com.homerun.domain.plan.type.StepTaskTemplate;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;
    private static final Long OPEN_STEP_ID = 100L;
    private static final Long LOCKED_STEP_ID = 200L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 2);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PlanRepository planRepository;

    @Mock
    private PlanStepRepository planStepRepository;

    @Mock
    private StepTaskRepository stepTaskRepository;

    @Mock
    private DeadlineRepository deadlineRepository;

    private DashboardService dashboardService;
    private Plan plan;

    @BeforeEach
    void setUp() {
        dashboardService =
                new DashboardService(planRepository, planStepRepository, stepTaskRepository, deadlineRepository, CLOCK);
        plan = Plan.create(MEMBER_ID, LeaseType.WOLSE, LocalDate.of(2027, 2, 1));
        ReflectionTestUtils.setField(plan, "id", PLAN_ID);
    }

    @Test
    @DisplayName("진행률은 관문이 아니라 할 일 기준으로 센다")
    void should_countSettledTasks_when_calculatingProgress() {
        givenPlanAndTasks(List.of(
                task("DONE", StepTaskStatus.DONE, 1),
                task("SKIPPED", StepTaskStatus.SKIPPED, 2),
                task("TODO", StepTaskStatus.TODO, 3)));

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.progress().completedTasks()).isEqualTo(2);
        assertThat(response.progress().totalTasks()).isEqualTo(3);
        assertThat(response.progress().progressPercent()).isEqualTo(66);
    }

    @Test
    void should_returnZeroProgress_when_planHasNoTasks() {
        givenPlanAndTasks(List.of());

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.progress().completedTasks()).isZero();
        assertThat(response.progress().totalTasks()).isZero();
        assertThat(response.progress().progressPercent()).isZero();
    }

    @Test
    void should_prioritizeRecalculationThenDoingThenTodo_when_tasksHaveDifferentStatuses() {
        givenPlanAndTasks(List.of(
                task("TODO", StepTaskStatus.TODO, 1),
                task("RECALC", StepTaskStatus.RECALC_REQUIRED, 3),
                task("DOING", StepTaskStatus.DOING, 2)));

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks())
                .extracting(DashboardTaskResponseStatus::of)
                .containsExactly(StepTaskStatus.RECALC_REQUIRED, StepTaskStatus.DOING, StepTaskStatus.TODO);
    }

    @Test
    void should_prioritizeStatusBeforeIrreversibility_when_tasksHaveNoDeadline() {
        StepTask todoIrreversible = task("TODO_IRREVERSIBLE", StepTaskStatus.TODO, 1);
        ReflectionTestUtils.setField(todoIrreversible, "irreversible", true);
        StepTask recalcReversible = task("RECALC_REVERSIBLE", StepTaskStatus.RECALC_REQUIRED, 2);
        givenPlanAndTasks(List.of(todoIrreversible, recalcReversible));

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks())
                .extracting(task -> task.taskCode())
                .containsExactly("RECALC_REVERSIBLE", "TODO_IRREVERSIBLE");
    }

    @Test
    void should_orderBySequence_when_tasksHaveSameStatus() {
        givenPlanAndTasks(List.of(task("TODO_FOUR", StepTaskStatus.TODO, 4), task("TODO_TWO", StepTaskStatus.TODO, 2)));

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks())
                .extracting(task -> task.sequence())
                .containsExactly(2, 4);
    }

    @Test
    void should_prioritizeEarlierDeadline_beforeStatusPriority() {
        StepTask todoSoon = task("TODO_SOON", StepTaskStatus.TODO, 1);
        StepTask recalcLater = task("RECALC_LATER", StepTaskStatus.RECALC_REQUIRED, 2);
        givenPlanAndTasks(List.of(recalcLater, todoSoon));
        givenDeadlines(
                deadline(recalcLater, "재계산 마감", TODAY.plusDays(5), false),
                deadline(todoSoon, "신청 마감", TODAY.plusDays(1), false));

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks())
                .extracting(task -> task.taskCode())
                .containsExactly("TODO_SOON", "RECALC_LATER");
    }

    @Test
    void should_prioritizeIrreversibleTask_when_dueDatesAreSame() {
        StepTask reversible = task("REVERSIBLE", StepTaskStatus.TODO, 1);
        StepTask irreversible = task("IRREVERSIBLE", StepTaskStatus.TODO, 2);
        ReflectionTestUtils.setField(irreversible, "irreversible", true);
        givenPlanAndTasks(List.of(reversible, irreversible));
        givenDeadlines(
                deadline(reversible, "서류 확인", TODAY.plusDays(3), false),
                deadline(irreversible, "전입신고", TODAY.plusDays(3), true));

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks())
                .extracting(task -> task.taskCode())
                .containsExactly("IRREVERSIBLE", "REVERSIBLE");
        assertThat(response.prioritizedTasks().get(0).priorityReason())
                .isEqualTo(DashboardTaskPriorityReason.IRREVERSIBLE_DEADLINE);
    }

    @Test
    void should_useNearestDeadlineAndReturnNegativeDays_when_deadlineIsOverdue() {
        StepTask todo = task("TODO", StepTaskStatus.TODO, 1);
        givenPlanAndTasks(List.of(todo));
        givenDeadlines(
                deadline(todo, "권장 마감", TODAY.plusDays(7), false), deadline(todo, "법정 마감", TODAY.minusDays(2), true));

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
    void should_chooseDeadlineTypeDeterministically_when_datesAndAbsoluteFlagsAreSame() {
        StepTask todo = task("TODO", StepTaskStatus.TODO, 1);
        givenPlanAndTasks(List.of(todo));
        givenDeadlines(
                deadline(2L, todo, "은행 처리", DeadlineType.PROCESSING, TODAY.plusDays(3), false),
                deadline(3L, todo, "법정 마감", DeadlineType.LEGAL, TODAY.plusDays(3), false));

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks().get(0).deadlineLabel()).isEqualTo("법정 마감");
    }

    @Test
    void should_chooseLowestDeadlineId_when_otherPriorityFieldsAreSame() {
        StepTask todo = task("TODO", StepTaskStatus.TODO, 1);
        givenPlanAndTasks(List.of(todo));
        givenDeadlines(
                deadline(20L, todo, "나중 ID", DeadlineType.RECOMMENDED, TODAY.plusDays(3), false),
                deadline(10L, todo, "먼저 ID", DeadlineType.RECOMMENDED, TODAY.plusDays(3), false));

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks().get(0).deadlineLabel()).isEqualTo("먼저 ID");
    }

    @Test
    void should_returnLastVisitedStageAndLocation_when_resumingPlan() {
        ReflectionTestUtils.setField(plan, "lastVisitedStage", PlanStage.SECOND);
        ReflectionTestUtils.setField(plan, "lastLocationCode", "POLICY_MATCHING");
        givenPlanAndTasks(List.of());

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.resume().stage()).isEqualTo(PlanStage.SECOND);
        assertThat(response.resume().locationCode()).isEqualTo("POLICY_MATCHING");
    }

    @Test
    void should_excludeSettledTasks_when_buildingTaskList() {
        givenPlanAndTasks(List.of(
                task("DONE", StepTaskStatus.DONE, 1),
                task("SKIPPED", StepTaskStatus.SKIPPED, 2),
                task("TODO", StepTaskStatus.TODO, 3),
                task("DOING", StepTaskStatus.DOING, 4),
                task("RECALC", StepTaskStatus.RECALC_REQUIRED, 5)));

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks())
                .extracting(task -> task.taskCode())
                .containsExactly("RECALC", "DOING", "TODO");
    }

    @Test
    @DisplayName("잠긴 관문 안의 할 일은 아직 할 수 없으므로 목록에서 뺀다")
    void should_excludeTasksOfLockedStep() {
        StepTask openTask = task("OPEN", StepTaskStatus.TODO, 1);
        StepTask lockedTask = task("LOCKED", StepTaskStatus.TODO, 2);
        ReflectionTestUtils.setField(lockedTask, "planStepId", LOCKED_STEP_ID);
        givenPlanAndTasks(List.of(openTask, lockedTask));

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks())
                .extracting(task -> task.taskCode())
                .containsExactly("OPEN");
    }

    @Test
    @DisplayName("할 일에는 속한 관문 코드를 함께 실어 보낸다")
    void should_exposeOwningStepCode() {
        givenPlanAndTasks(List.of(task("TODO", StepTaskStatus.TODO, 1)));

        DashboardResponse response = dashboardService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.prioritizedTasks().get(0).stepCode()).isEqualTo("OPEN_STEP");
    }

    @Test
    void should_throwPlanNotFound_when_planDoesNotExist() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.get(MEMBER_ID, PLAN_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        verify(stepTaskRepository, never()).findAllByPlanId(PLAN_ID);
    }

    @Test
    void should_throwPlanAccessDenied_when_planBelongsToAnotherMember() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> dashboardService.get(999L, PLAN_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_ACCESS_DENIED));
        verify(stepTaskRepository, never()).findAllByPlanId(PLAN_ID);
    }

    private void givenPlanAndTasks(List<StepTask> tasks) {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(planStepRepository.findAllByPlanIdOrderBySequenceAsc(PLAN_ID))
                .thenReturn(List.of(
                        step(OPEN_STEP_ID, "OPEN_STEP", PlanStepStatus.READY),
                        step(LOCKED_STEP_ID, "LOCKED_STEP", PlanStepStatus.LOCKED)));
        when(stepTaskRepository.findAllByPlanId(PLAN_ID)).thenReturn(tasks);
    }

    private void givenDeadlines(Deadline... deadlines) {
        when(deadlineRepository.findAllByPlanIdAndTaskIdIsNotNull(PLAN_ID)).thenReturn(List.of(deadlines));
    }

    private PlanStep step(Long id, String code, PlanStepStatus status) {
        PlanStep step = PlanStep.defaultSteps(PLAN_ID).get(0);
        ReflectionTestUtils.setField(step, "id", id);
        ReflectionTestUtils.setField(step, "stepCode", code);
        ReflectionTestUtils.setField(step, "stepName", code);
        ReflectionTestUtils.setField(step, "status", status);
        return step;
    }

    private StepTask task(String code, StepTaskStatus status, int sequence) {
        StepTask task = StepTask.of(OPEN_STEP_ID, StepTaskTemplate.AGREE_TERMS);
        ReflectionTestUtils.setField(task, "id", (long) sequence);
        ReflectionTestUtils.setField(task, "taskCode", code);
        ReflectionTestUtils.setField(task, "taskName", code);
        ReflectionTestUtils.setField(task, "status", status);
        ReflectionTestUtils.setField(task, "sequence", sequence);
        ReflectionTestUtils.setField(task, "irreversible", false);
        return task;
    }

    private Deadline deadline(StepTask task, String label, LocalDate dueDate, boolean absolute) {
        return deadline(null, task, label, absolute ? DeadlineType.LEGAL : DeadlineType.RECOMMENDED, dueDate, absolute);
    }

    private Deadline deadline(
            Long id, StepTask task, String label, DeadlineType type, LocalDate dueDate, boolean absolute) {
        Deadline deadline = mock(Deadline.class, withSettings().lenient());
        when(deadline.getId()).thenReturn(id);
        when(deadline.getTaskId()).thenReturn(task.getId());
        when(deadline.getType()).thenReturn(type);
        when(deadline.getLabel()).thenReturn(label);
        when(deadline.getDueDate()).thenReturn(dueDate);
        when(deadline.isAbsolute()).thenReturn(absolute);
        return deadline;
    }

    /** {@code extracting} 안에서 쓰기 위한 작은 헬퍼. 람다 타입 추론이 status 에서 막힌다. */
    private interface DashboardTaskResponseStatus {
        static StepTaskStatus of(com.homerun.domain.dashboard.dto.response.DashboardTaskResponse task) {
            return task.status();
        }
    }
}
