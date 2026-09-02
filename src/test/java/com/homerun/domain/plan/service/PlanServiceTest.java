package com.homerun.domain.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.dto.request.CompletePlanStepRequest;
import com.homerun.domain.plan.dto.request.UpdatePlanLocationRequest;
import com.homerun.domain.plan.dto.response.PlanProgressResponse;
import com.homerun.domain.plan.dto.response.PlanResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.policy.PlanStageTransitionPolicy;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.PlanGate;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.plan.type.PlanStatus;
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

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private PlanStepRepository planStepRepository;

    private PlanService planService;
    private Plan plan;
    private List<PlanStep> steps;

    @BeforeEach
    void setUp() {
        planService = new PlanService(planRepository, planStepRepository, new PlanStageTransitionPolicy());
        plan = Plan.create(MEMBER_ID, LeaseType.JEONSE, LocalDate.of(2027, 2, 1));
        steps = PlanStep.defaultSteps(PLAN_ID);
    }

    @Test
    void should_advanceAndUnlockNextStep_when_currentGateIsCompleted() {
        givenPlanOwnedByMember();

        PlanProgressResponse response = planService.completeStep(
                MEMBER_ID, PLAN_ID, "BENCH_ONBOARDING", new CompletePlanStepRequest(Plan.CURRENT_RULE_VERSION));

        assertThat(plan.getStage()).isEqualTo(PlanStage.FIRST);
        assertThat(steps.get(0).getStatus()).isEqualTo(PlanStepStatus.DONE);
        assertThat(steps.get(1).getStatus()).isEqualTo(PlanStepStatus.READY);
        assertThat(response.progressPercent()).isEqualTo(20);
    }

    @Test
    void should_rejectCompletion_when_stepIsLocked() {
        givenPlanOwnedByMember();

        assertThatThrownBy(() -> planService.completeStep(
                        MEMBER_ID, PLAN_ID, "FIRST_DIAGNOSIS", new CompletePlanStepRequest(Plan.CURRENT_RULE_VERSION)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_STEP_LOCKED));
        assertThat(plan.getStage()).isEqualTo(PlanStage.BENCH);
    }

    @Test
    void should_rejectCompletion_when_ruleVersionDiffers() {
        givenPlanExists();

        assertThatThrownBy(() -> planService.completeStep(
                        MEMBER_ID, PLAN_ID, "BENCH_ONBOARDING", new CompletePlanStepRequest("outdated")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RULE_VERSION_MISMATCH));
    }

    @Test
    void should_rejectAccess_when_planBelongsToAnotherMember() {
        givenPlanExists();

        assertThatThrownBy(() -> planService.getProgress(999L, PLAN_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_ACCESS_DENIED));
    }

    @Test
    void should_keepSameStage_when_completedStepIsRequestedAgain() {
        givenPlanOwnedByMember();
        CompletePlanStepRequest request = new CompletePlanStepRequest(Plan.CURRENT_RULE_VERSION);

        planService.completeStep(MEMBER_ID, PLAN_ID, "BENCH_ONBOARDING", request);
        PlanProgressResponse secondResponse = planService.completeStep(MEMBER_ID, PLAN_ID, "BENCH_ONBOARDING", request);

        assertThat(plan.getStage()).isEqualTo(PlanStage.FIRST);
        assertThat(secondResponse.completedSteps()).isEqualTo(1);
        assertThat(secondResponse.progressPercent()).isEqualTo(20);
    }

    @Test
    void should_finishPlan_when_allGatesAreCompletedInOrder() {
        givenPlanOwnedByMember();
        CompletePlanStepRequest request = new CompletePlanStepRequest(Plan.CURRENT_RULE_VERSION);

        for (PlanGate gate : PlanGate.values()) {
            planService.completeStep(MEMBER_ID, PLAN_ID, gate.code(), request);
        }

        assertThat(plan.getStage()).isEqualTo(PlanStage.HOME);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.DONE);
        assertThat(steps).allMatch(step -> step.getStatus() == PlanStepStatus.DONE);
    }

    @Test
    void should_restoreInitialProgress_when_planIsReset() {
        givenPlanOwnedByMember();
        planService.completeStep(
                MEMBER_ID, PLAN_ID, "BENCH_ONBOARDING", new CompletePlanStepRequest(Plan.CURRENT_RULE_VERSION));
        plan.updateLastLocation("DIA_INCOME");

        PlanProgressResponse response = planService.reset(MEMBER_ID, PLAN_ID);

        assertThat(plan.getStage()).isEqualTo(PlanStage.BENCH);
        assertThat(plan.getLastVisitedStage()).isEqualTo(PlanStage.BENCH);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(plan.getLastLocationCode()).isNull();
        assertThat(steps.get(0).getStatus()).isEqualTo(PlanStepStatus.READY);
        assertThat(steps.subList(1, steps.size())).allMatch(step -> step.getStatus() == PlanStepStatus.LOCKED);
        assertThat(response.progressPercent()).isZero();
    }

    @Test
    void should_enterCompletedPreviousStage_withoutChangingCurrentStage() {
        givenPlanOwnedByMember();
        completeGate("BENCH_ONBOARDING");
        completeGate("FIRST_DIAGNOSIS");

        PlanResponse response = planService.enterStage(
                MEMBER_ID, PLAN_ID, PlanStage.BENCH, new UpdatePlanLocationRequest("BENCH_SUMMARY"));

        assertThat(response.stage()).isEqualTo(PlanStage.SECOND);
        assertThat(response.lastVisitedStage()).isEqualTo(PlanStage.BENCH);
        assertThat(response.lastLocationCode()).isEqualTo("BENCH_SUMMARY");
        assertThat(plan.getStage()).isEqualTo(PlanStage.SECOND);
    }

    @Test
    void should_enterCurrentStage_when_stageIsAlreadyUnlocked() {
        givenPlanOwnedByMember();
        completeGate("BENCH_ONBOARDING");

        PlanResponse response = planService.enterStage(
                MEMBER_ID, PLAN_ID, PlanStage.FIRST, new UpdatePlanLocationRequest("DIA_INCOME"));

        assertThat(response.stage()).isEqualTo(PlanStage.FIRST);
        assertThat(response.lastVisitedStage()).isEqualTo(PlanStage.FIRST);
        assertThat(response.lastLocationCode()).isEqualTo("DIA_INCOME");
    }

    @Test
    void should_rejectEntry_when_targetStageIsStillLocked() {
        givenPlanOwnedByMember();
        completeGate("BENCH_ONBOARDING");

        assertThatThrownBy(() -> planService.enterStage(
                        MEMBER_ID, PLAN_ID, PlanStage.THIRD, new UpdatePlanLocationRequest("CONTRACT")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_STAGE_LOCKED));
        assertThat(plan.getStage()).isEqualTo(PlanStage.FIRST);
        assertThat(plan.getLastVisitedStage()).isEqualTo(PlanStage.FIRST);
        assertThat(plan.getLastLocationCode()).isNull();
    }

    @Test
    void should_restorePreviousStageLocation_when_planIsReadAgain() {
        givenPlanOwnedByMember();
        completeGate("BENCH_ONBOARDING");
        planService.enterStage(MEMBER_ID, PLAN_ID, PlanStage.BENCH, new UpdatePlanLocationRequest("BENCH_REVIEW"));

        PlanResponse response = planService.get(MEMBER_ID, PLAN_ID);

        assertThat(response.stage()).isEqualTo(PlanStage.FIRST);
        assertThat(response.lastVisitedStage()).isEqualTo(PlanStage.BENCH);
        assertThat(response.lastLocationCode()).isEqualTo("BENCH_REVIEW");
    }

    private void givenPlanOwnedByMember() {
        givenPlanExists();
        when(planStepRepository.findAllByPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(steps);
    }

    private void givenPlanExists() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    }

    private void completeGate(String stepCode) {
        planService.completeStep(MEMBER_ID, PLAN_ID, stepCode, new CompletePlanStepRequest(Plan.CURRENT_RULE_VERSION));
    }
}
