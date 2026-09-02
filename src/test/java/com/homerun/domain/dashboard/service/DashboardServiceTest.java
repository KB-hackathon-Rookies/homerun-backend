package com.homerun.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.dashboard.dto.response.DashboardResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.PlanStepStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.LocalDate;
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

    @Mock
    private PlanRepository planRepository;

    @Mock
    private PlanStepRepository planStepRepository;

    private DashboardService dashboardService;
    private Plan plan;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(planRepository, planStepRepository);
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
        return step;
    }
}
