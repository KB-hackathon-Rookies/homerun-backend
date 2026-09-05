package com.homerun.domain.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.dto.request.FinancialIncomeConfirmationRequest;
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
import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.FinancialIncomeAction;
import com.homerun.domain.plan.type.FinancialValueSource;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.HouseholderStatus;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.domain.plan.type.OpenBankingIncomeSyncStatus;
import com.homerun.domain.plan.type.PlanGate;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import com.homerun.domain.plan.type.PlanStepStatus;
import com.homerun.domain.region.repository.RegionRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Mock
    private RegionRepository regionRepository;

    private PlanInputService inputService;
    private Plan plan;

    @BeforeEach
    void setUp() {
        inputService = new PlanInputService(
                planRepository, inputRepository, historyRepository, stepRepository, regionRepository);
        plan = Plan.create(MEMBER_ID, LeaseType.JEONSE, null);
        org.mockito.Mockito.lenient().when(regionRepository.existsById(1L)).thenReturn(true);
    }

    @Test
    void should_saveAndRestoreUnknownFields_when_inputIsCreated() {
        givenOwnedPlan();
        PlanInputRequest request = request(
                100_000_000L, null, Set.of(PlanInputUnknownField.MONTHLY_RENT, PlanInputUnknownField.MAINTENANCE_FEE));
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
        when(inputRepository.save(any(PlanInput.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlanInputResponse saved = inputService.save(MEMBER_ID, PLAN_ID, request);
        ArgumentCaptor<PlanInput> inputCaptor = ArgumentCaptor.forClass(PlanInput.class);
        verify(inputRepository).save(inputCaptor.capture());
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(inputCaptor.getValue()));
        PlanInputResponse restored = inputService.get(MEMBER_ID, PLAN_ID);

        assertThat(saved.revision()).isEqualTo(1);
        assertThat(saved.saveStatus()).isEqualTo("SAVED");
        assertThat(restored.unknownFields())
                .containsExactly(PlanInputUnknownField.MAINTENANCE_FEE, PlanInputUnknownField.MONTHLY_RENT);
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
    void should_rejectUnknownField_when_valueIsAlsoProvided() {
        PlanInputRequest request = request(null, 500_000L, Set.of(PlanInputUnknownField.MONTHLY_RENT));

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

    @Test
    void should_rejectInput_when_regionDoesNotExist() {
        givenOwnedPlan();
        PlanInputRequest request = request(100_000_000L, 500_000L, 999L, Set.of());

        assertThatThrownBy(() -> inputService.save(MEMBER_ID, PLAN_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_REGION_NOT_FOUND));
        verifyNoInteractions(inputRepository);
    }

    @Test
    void should_createUnconfirmedOpenBankingIncome_when_inputDoesNotExist() {
        givenOwnedPlan();
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
        when(inputRepository.save(any(PlanInput.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stepRepository.findAllByPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of());

        var result = inputService.syncOpenBankingIncome(MEMBER_ID, PLAN_ID, 2_900_000L);

        assertThat(result.status()).isEqualTo(OpenBankingIncomeSyncStatus.APPLIED);
        assertThat(result.input().monthlyIncome()).isEqualTo(2_900_000L);
        assertThat(result.input().incomeSource()).isEqualTo(FinancialValueSource.OPEN_BANKING);
        assertThat(result.input().financialDataConfirmed()).isFalse();
    }

    @Test
    void should_preserveManualIncome_when_openBankingIsSynced() {
        givenOwnedPlan();
        PlanInput input = PlanInput.create(PLAN_ID, request(100_000_000L, 500_000L, Set.of()));
        ReflectionTestUtils.setField(input, "incomeSource", FinancialValueSource.MANUAL);
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));

        var result = inputService.syncOpenBankingIncome(MEMBER_ID, PLAN_ID, 2_900_000L);

        assertThat(result.status()).isEqualTo(OpenBankingIncomeSyncStatus.MANUAL_VALUE_PRESERVED);
        assertThat(result.input().monthlyIncome()).isEqualTo(3_000_000L);
        verifyNoInteractions(historyRepository, stepRepository);
    }

    @Test
    void should_storePreviousIncomeAndRequireRecalculation_when_syncChangesValue() {
        givenOwnedPlan();
        PlanInput input = PlanInput.create(PLAN_ID, request(100_000_000L, 500_000L, Set.of()));
        ReflectionTestUtils.setField(input, "incomeSource", FinancialValueSource.OPEN_BANKING);
        ReflectionTestUtils.setField(input, "financialDataConfirmed", false);
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));
        when(historyRepository.save(any(PlanInputHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stepRepository.findAllByPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of());

        var result = inputService.syncOpenBankingIncome(MEMBER_ID, PLAN_ID, 2_900_000L);

        ArgumentCaptor<PlanInputHistory> history = ArgumentCaptor.forClass(PlanInputHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getSnapshot()).containsEntry("monthlyIncome", 3_000_000L);
        assertThat(result.input().monthlyIncome()).isEqualTo(2_900_000L);
        assertThat(result.input().financialDataConfirmed()).isFalse();
        assertThat(result.input().revision()).isEqualTo(2);
    }

    @Test
    void should_confirmOnlyStoredOpenBankingIncome() {
        givenOwnedPlan();
        PlanInput input = PlanInput.createWithOpenBankingIncome(PLAN_ID, 2_900_000L);
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));
        when(historyRepository.save(any(PlanInputHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stepRepository.findAllByPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of());

        PlanInputResponse result = inputService.confirmFinancialIncome(
                MEMBER_ID,
                PLAN_ID,
                new FinancialIncomeConfirmationRequest(FinancialIncomeAction.CONFIRM_OPEN_BANKING, null));

        assertThat(result.monthlyIncome()).isEqualTo(2_900_000L);
        assertThat(result.incomeSource()).isEqualTo(FinancialValueSource.OPEN_BANKING);
        assertThat(result.financialDataConfirmed()).isTrue();
        assertThat(result.revision()).isEqualTo(2);
        ArgumentCaptor<PlanInputHistory> previous = ArgumentCaptor.forClass(PlanInputHistory.class);
        verify(historyRepository).save(previous.capture());
        assertThat(previous.getValue().getSnapshot()).containsEntry("financialDataConfirmed", false);
    }

    @Test
    void should_notCreateAnotherRevision_whenOpenBankingIncomeIsAlreadyConfirmed() {
        givenOwnedPlan();
        PlanInput input = PlanInput.createWithOpenBankingIncome(PLAN_ID, 2_900_000L);
        input.confirmOpenBankingIncome();
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));

        PlanInputResponse result = inputService.confirmFinancialIncome(
                MEMBER_ID,
                PLAN_ID,
                new FinancialIncomeConfirmationRequest(FinancialIncomeAction.CONFIRM_OPEN_BANKING, null));

        assertThat(result.revision()).isEqualTo(2);
        verifyNoInteractions(historyRepository, stepRepository);
    }

    @Test
    void should_rejectClientSuppliedAmount_when_confirmingOpenBankingIncome() {
        givenOwnedPlan();
        when(inputRepository.findByPlanId(PLAN_ID))
                .thenReturn(Optional.of(PlanInput.createWithOpenBankingIncome(PLAN_ID, 2_900_000L)));

        assertThatThrownBy(() -> inputService.confirmFinancialIncome(
                        MEMBER_ID,
                        PLAN_ID,
                        new FinancialIncomeConfirmationRequest(FinancialIncomeAction.CONFIRM_OPEN_BANKING, 1L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.INVALID_FINANCIAL_INCOME_CONFIRMATION));
        verifyNoInteractions(historyRepository, stepRepository);
    }

    @Test
    void should_replaceSyncedIncomeWithManualValue_withoutConfirmingOtherExternalAssets() {
        givenOwnedPlan();
        PlanInput input = PlanInput.create(PLAN_ID, request(100_000_000L, 500_000L, Set.of()));
        ReflectionTestUtils.setField(input, "assetSource", FinancialValueSource.OPEN_BANKING);
        ReflectionTestUtils.setField(input, "financialDataConfirmed", true);
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));
        when(historyRepository.save(any(PlanInputHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stepRepository.findAllByPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of());

        PlanInputResponse result = inputService.confirmFinancialIncome(
                MEMBER_ID,
                PLAN_ID,
                new FinancialIncomeConfirmationRequest(FinancialIncomeAction.USE_MANUAL, 3_100_000L));

        assertThat(result.monthlyIncome()).isEqualTo(3_100_000L);
        assertThat(result.incomeSource()).isEqualTo(FinancialValueSource.MANUAL);
        assertThat(result.financialDataConfirmed()).isFalse();
        assertThat(result.netAssets()).isEqualTo(100_000_000L);
        assertThat(result.assetSource()).isEqualTo(FinancialValueSource.OPEN_BANKING);
    }

    @Test
    void should_rejectIncomeOnlyConfirmation_whenExternalAssetAlsoNeedsConfirmation() {
        givenOwnedPlan();
        PlanInput input = PlanInput.createWithOpenBankingIncome(PLAN_ID, 2_900_000L);
        ReflectionTestUtils.setField(input, "assetSource", FinancialValueSource.OPEN_BANKING);
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));

        assertThatThrownBy(() -> inputService.confirmFinancialIncome(
                        MEMBER_ID,
                        PLAN_ID,
                        new FinancialIncomeConfirmationRequest(FinancialIncomeAction.CONFIRM_OPEN_BANKING, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.INVALID_FINANCIAL_INCOME_CONFIRMATION));
    }

    @Test
    void should_rejectForgedOpenBankingIncome_fromFullSnapshotApi() {
        givenOwnedPlan();
        PlanInputRequest forged =
                requestWithFinancial(FinancialValueSource.OPEN_BANKING, FinancialValueSource.MANUAL, false);

        assertThatThrownBy(() -> inputService.save(MEMBER_ID, PLAN_ID, forged))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.INVALID_FINANCIAL_INCOME_CONFIRMATION));
    }

    @Test
    void should_rejectForgedOpenBankingAsset_fromFullSnapshotApi() {
        givenOwnedPlan();
        PlanInputRequest forged =
                requestWithFinancial(FinancialValueSource.MANUAL, FinancialValueSource.OPEN_BANKING, false);

        assertThatThrownBy(() -> inputService.save(MEMBER_ID, PLAN_ID, forged))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.INVALID_FINANCIAL_INCOME_CONFIRMATION));
    }

    @Test
    void should_allowConfirmingUnchangedServerSyncedIncome_fromFullSnapshotApi() {
        givenOwnedPlan();
        PlanInput input = PlanInput.createWithOpenBankingIncome(PLAN_ID, 3_000_000L);
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));
        when(historyRepository.save(any(PlanInputHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stepRepository.findAllByPlanIdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of());
        PlanInputRequest confirmed = requestWithFinancial(FinancialValueSource.OPEN_BANKING, null, true);

        PlanInputResponse result = inputService.save(MEMBER_ID, PLAN_ID, confirmed);

        assertThat(result.monthlyIncome()).isEqualTo(3_000_000L);
        assertThat(result.financialDataConfirmed()).isTrue();
    }

    private void givenOwnedPlan() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    }

    private PlanInputRequest request(Long hopeDeposit, Long monthlyRent, Set<PlanInputUnknownField> unknownFields) {
        return request(hopeDeposit, monthlyRent, 1L, unknownFields);
    }

    private PlanInputRequest request(
            Long hopeDeposit, Long monthlyRent, Long regionId, Set<PlanInputUnknownField> unknownFields) {
        return new PlanInputRequest(
                hopeDeposit,
                20_000_000L,
                monthlyRent,
                null,
                800_000L,
                regionId,
                new BigDecimal("84.92"),
                HouseType.APARTMENT,
                true,
                HouseholderStatus.CURRENT,
                MaritalStatus.SINGLE,
                EmploymentType.FULL_TIME,
                12,
                CompanySize.SMALL,
                true,
                LocalDate.of(2000, 1, 1),
                18,
                3_000_000L,
                100_000_000L,
                20_000_000L,
                false,
                FinancialValueSource.MANUAL,
                FinancialValueSource.MANUAL,
                false,
                unknownFields);
    }

    private PlanInputRequest requestWithFinancial(
            FinancialValueSource incomeSource, FinancialValueSource assetSource, boolean confirmed) {
        return new PlanInputRequest(
                100_000_000L,
                20_000_000L,
                500_000L,
                null,
                800_000L,
                1L,
                new BigDecimal("84.92"),
                HouseType.APARTMENT,
                true,
                HouseholderStatus.CURRENT,
                MaritalStatus.SINGLE,
                EmploymentType.FULL_TIME,
                12,
                CompanySize.SMALL,
                true,
                LocalDate.of(2000, 1, 1),
                18,
                3_000_000L,
                100_000_000L,
                20_000_000L,
                false,
                incomeSource,
                assetSource,
                confirmed,
                Set.of());
    }
}
