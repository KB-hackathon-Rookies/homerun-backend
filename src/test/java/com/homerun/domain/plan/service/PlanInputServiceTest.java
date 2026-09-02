package com.homerun.domain.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.dto.response.PlanInputResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.entity.PlanInputHistory;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.repository.PlanInputHistoryRepository;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.PlanGate;
import com.homerun.domain.plan.type.PlanStepStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanInputServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private PlanInputRepository inputRepository;

    @Mock
    private PlanInputHistoryRepository historyRepository;

    @Mock
    private PlanStepRepository stepRepository;

    private PlanInputService inputService;
    private Plan plan;

    @BeforeEach
    void setUp() {
        inputService = new PlanInputService(planRepository, inputRepository, historyRepository, stepRepository);
        plan = Plan.create(MEMBER_ID, LeaseType.JEONSE, null);
    }

    @Test
    void should_saveAndRestoreUnknownFields_when_inputIsCreated() {
        givenOwnedPlan();
        PlanInputRequest request = request(100_000_000L, null, Set.of("monthlyRent", "maintenanceFee"));
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
        when(inputRepository.save(any(PlanInput.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlanInputResponse saved = inputService.save(MEMBER_ID, PLAN_ID, request);
        ArgumentCaptor<PlanInput> inputCaptor = ArgumentCaptor.forClass(PlanInput.class);
        verify(inputRepository).save(inputCaptor.capture());
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(inputCaptor.getValue()));
        PlanInputResponse restored = inputService.get(MEMBER_ID, PLAN_ID);

        assertThat(saved.revision()).isEqualTo(1);
        assertThat(saved.saveStatus()).isEqualTo("SAVED");
        assertThat(restored.unknownFields()).containsExactly("maintenanceFee", "monthlyRent");
        assertThat(restored.monthlyRent()).isNull();
    }

    @Test
    void should_storePreviousRevision_when_inputChanges() {
        givenOwnedPlan();
        PlanInput input = PlanInput.create(PLAN_ID, request(100_000_000L, 500_000L, Set.of()));
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));
        when(historyRepository.save(any(PlanInputHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stepRepository.findAllByPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of());

        PlanInputResponse response = inputService.save(MEMBER_ID, PLAN_ID, request(120_000_000L, 500_000L, Set.of()));

        ArgumentCaptor<PlanInputHistory> historyCaptor = ArgumentCaptor.forClass(PlanInputHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getRevision()).isEqualTo(1);
        assertThat(historyCaptor.getValue().getSnapshot()).containsEntry("hopeDeposit", 100_000_000L);
        assertThat(response.revision()).isEqualTo(2);
        assertThat(response.hopeDeposit()).isEqualTo(120_000_000L);
    }

    @Test
    void should_notCreateHistory_when_sameSnapshotIsSavedAgain() {
        givenOwnedPlan();
        PlanInputRequest request = request(100_000_000L, 500_000L, Set.of());
        PlanInput input = PlanInput.create(PLAN_ID, request);
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));

        PlanInputResponse response = inputService.save(MEMBER_ID, PLAN_ID, request);

        assertThat(response.revision()).isEqualTo(1);
        verifyNoInteractions(historyRepository, stepRepository);
    }

    @Test
    void should_markCompletedDiagnosisForRecalculation_when_upstreamInputChanges() {
        givenOwnedPlan();
        PlanInput input = PlanInput.create(PLAN_ID, request(100_000_000L, 500_000L, Set.of()));
        List<PlanStep> steps = PlanStep.defaultSteps(PLAN_ID);
        steps.get(0).complete();
        steps.get(1).unlockWhenDependenciesCompleted(List.of(PlanGate.BENCH_ONBOARDING.code()));
        steps.get(1).complete();
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));
        when(historyRepository.save(any(PlanInputHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stepRepository.findAllByPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(steps);

        inputService.save(MEMBER_ID, PLAN_ID, request(120_000_000L, 500_000L, Set.of()));

        assertThat(steps.get(0).getStatus()).isEqualTo(PlanStepStatus.DONE);
        assertThat(steps.get(1).getStatus()).isEqualTo(PlanStepStatus.RECALC_REQUIRED);
    }

    @Test
    void should_rejectUnknownField_when_fieldNameIsNotSupported() {
        PlanInputRequest request = request(null, null, Set.of("notExistingField"));

        assertThatThrownBy(() -> inputService.save(MEMBER_ID, PLAN_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_UNKNOWN_FIELD));
    }

    @Test
    void should_rejectUnknownField_when_valueIsAlsoProvided() {
        PlanInputRequest request = request(null, 500_000L, Set.of("monthlyRent"));

        assertThatThrownBy(() -> inputService.save(MEMBER_ID, PLAN_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNKNOWN_FIELD_HAS_VALUE));
    }

    @Test
    void should_rejectInputAccess_when_planBelongsToAnotherMember() {
        givenOwnedPlan();

        assertThatThrownBy(() -> inputService.get(999L, PLAN_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_ACCESS_DENIED));
    }

    private void givenOwnedPlan() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    }

    private PlanInputRequest request(Long hopeDeposit, Long monthlyRent, Set<String> unknownFields) {
        return new PlanInputRequest(
                hopeDeposit,
                20_000_000L,
                monthlyRent,
                null,
                800_000L,
                1L,
                new BigDecimal("84.92"),
                "APARTMENT",
                true,
                "HOUSEHOLDER",
                "SINGLE",
                "EMPLOYEE",
                12,
                "SMALL",
                unknownFields);
    }
}
