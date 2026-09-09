package com.homerun.domain.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.diagnosis.dto.request.DiagnosisCalculationRequest;
import com.homerun.domain.diagnosis.repository.CostEstimateRepository;
import com.homerun.domain.diagnosis.repository.DiagnosisRepository;
import com.homerun.domain.diagnosis.type.DiagnosisVerdict;
import com.homerun.domain.diagnosis.type.DiagnosisWarning;
import com.homerun.domain.openbanking.entity.FinancialSnapshot;
import com.homerun.domain.openbanking.repository.FinancialSnapshotRepository;
import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.FinancialValueSource;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.policy.dto.response.AncillaryCostResponse;
import com.homerun.domain.policy.service.AncillaryCostCalculator;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiagnosisServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 5);

    @Mock
    PlanRepository plans;

    @Mock
    PlanInputRepository inputs;

    @Mock
    FinancialSnapshotRepository snapshots;

    @Mock
    CostEstimateRepository costs;

    @Mock
    DiagnosisRepository diagnoses;

    @Mock
    AncillaryCostCalculator ancillaryCosts;

    private DiagnosisService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);
        service = new DiagnosisService(plans, inputs, snapshots, costs, diagnoses, ancillaryCosts, clock);
    }

    @Test
    void should_calculatePossibleDiagnosisWithoutRir_whenFundsCoverRequiredCash() {
        givenPlanAndInput(input(100_000_000L, 3_000_000L, 20_000_000L, 5_000_000L, true));

        var result = service.simulate(MEMBER_ID, PLAN_ID, request(200_000L));

        assertThat(result.initialCost().totalRequired()).isEqualTo(104_875_000L);
        assertThat(result.policyComparison().requiredCashAfterPolicy()).isEqualTo(24_875_000L);
        assertThat(result.policyComparison().reducedInitialCash()).isEqualTo(80_000_000L);
        assertThat(result.policyComparison().monthlyHousingCostBeforePolicy()).isEqualTo(100_000L);
        assertThat(result.policyComparison().monthlyHousingCostAfterPolicy()).isEqualTo(300_000L);
        assertThat(result.policyComparison().monthlyDisposableBeforePolicy()).isEqualTo(1_800_000L);
        assertThat(result.policyComparison().monthlyDisposableAfterPolicy()).isEqualTo(1_600_000L);
        assertThat(result.policyComparison().possibleDateBeforePolicy()).isEqualTo(TODAY.plusMonths(45));
        assertThat(result.policyComparison().possibleDateAfterPolicy()).isEqualTo(TODAY);
        assertThat(result.monthlyHousingCost()).isEqualTo(300_000L);
        assertThat(result.monthlyDisposable()).isEqualTo(1_600_000L);
        assertThat(result.monthsToMove()).isEqualTo(6);
        assertThat(result.savableAmount()).isEqualTo(9_600_000L);
        assertThat(result.expectedFund()).isEqualTo(34_600_000L);
        assertThat(result.shortfall()).isZero();
        assertThat(result.possibleDate()).isEqualTo(TODAY);
        assertThat(result.verdict()).isEqualTo(DiagnosisVerdict.POSSIBLE);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.engineVersion()).isEqualTo("DIA-02-1.0");
        verify(costs, never()).save(org.mockito.ArgumentMatchers.any());
        verify(diagnoses, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void should_returnCautionAndPossibleDate_whenTargetDateFundsAreShort() {
        givenPlanAndInput(input(100_000_000L, 2_000_000L, 5_000_000L, 0L, true));

        var result = service.simulate(MEMBER_ID, PLAN_ID, request(200_000L));

        assertThat(result.monthlyDisposable()).isEqualTo(600_000L);
        assertThat(result.expectedFund()).isEqualTo(8_600_000L);
        assertThat(result.shortfall()).isEqualTo(16_275_000L);
        assertThat(result.possibleDate()).isEqualTo(TODAY.plusMonths(34));
        assertThat(result.verdict()).isEqualTo(DiagnosisVerdict.CAUTION);
    }

    @Test
    void should_returnDifficultWithoutPossibleDate_whenMonthlyCashFlowIsNegative() {
        givenPlanAndInput(input(100_000_000L, 1_000_000L, 5_000_000L, 0L, true));

        var result = service.simulate(MEMBER_ID, PLAN_ID, request(400_000L));

        assertThat(result.monthlyDisposable()).isEqualTo(-600_000L);
        assertThat(result.savableAmount()).isZero();
        assertThat(result.possibleDate()).isNull();
        assertThat(result.verdict()).isEqualTo(DiagnosisVerdict.DIFFICULT);
    }

    @Test
    void should_useLatestDebtPaymentAsUnconfirmed_whenRequestOmitsIt() {
        givenPlanAndInput(input(100_000_000L, 3_000_000L, 20_000_000L, 5_000_000L, true));
        FinancialSnapshot snapshot = org.mockito.Mockito.mock(FinancialSnapshot.class);
        when(snapshot.getMonthlyDebtPayment()).thenReturn(350_000L);
        when(snapshots.findFirstByUserIdOrderByCreatedAtDescIdDesc(MEMBER_ID)).thenReturn(Optional.of(snapshot));

        var request = new DiagnosisCalculationRequest(
                1_000_000L, 500_000L, 300_000L, 75_000L, 3_000_000L, 900_000L, null, 80_000_000L, 200_000L);
        var result = service.simulate(MEMBER_ID, PLAN_ID, request);

        assertThat(result.monthlyDebtPayment()).isEqualTo(350_000L);
        assertThat(result.warnings()).containsExactly(DiagnosisWarning.MONTHLY_DEBT_PAYMENT_UNCONFIRMED);
        assertThat(result.verdict()).isEqualTo(DiagnosisVerdict.CAUTION);
    }

    @Test
    void should_markOpenBankingIncomeAsUnconfirmed_insteadOfReturningPossible() {
        givenPlanAndInput(input(100_000_000L, 3_000_000L, 20_000_000L, 5_000_000L, false));

        var result = service.simulate(MEMBER_ID, PLAN_ID, request(200_000L));

        assertThat(result.shortfall()).isZero();
        assertThat(result.warnings()).containsExactly(DiagnosisWarning.OPEN_BANKING_INCOME_UNCONFIRMED);
        assertThat(result.verdict()).isEqualTo(DiagnosisVerdict.CAUTION);
    }

    @Test
    void should_rejectCalculation_whenRequiredPlanInputIsMissing() {
        Plan plan = Plan.create(MEMBER_ID, LeaseType.JEONSE, TODAY.plusMonths(6));
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input(null, 3_000_000L, 20_000_000L, 0L, true)));

        assertThatThrownBy(() -> service.simulate(MEMBER_ID, PLAN_ID, request(200_000L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.DIAGNOSIS_INPUT_REQUIRED));
    }

    private void givenPlanAndInput(PlanInput input) {
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, TODAY.plusMonths(6))));
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));
    }

    private PlanInput input(Long deposit, long income, long availableCash, long currentDeposit, boolean confirmed) {
        return PlanInput.create(
                PLAN_ID,
                new PlanInputRequest(
                        deposit,
                        currentDeposit,
                        0L,
                        100_000L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        income,
                        null,
                        availableCash,
                        null,
                        confirmed ? FinancialValueSource.MANUAL : FinancialValueSource.OPEN_BANKING,
                        null,
                        confirmed,
                        Set.of()));
    }

    /**
     * 0 은 "확인해서 0원" 이고 null 은 "모른다" 다. 화면은 대출금이 정해져야 나오는 중개보수·인지세·
     * 보증료를 알 수 없어 전에는 전부 0 을 보냈고, 그 0 이 그대로 초기 필요자금에서 빠졌다.
     */
    @Test
    void should_estimateAncillaryCosts_whenRequestOmitsThem() {
        givenPlanAndInput(input(100_000_000L, 3_000_000L, 20_000_000L, 5_000_000L, true));
        when(ancillaryCosts.ancillaryCost(100_000_000L, 80_000_000L, null, null, null))
                .thenReturn(new AncillaryCostResponse(880_000L, 70_000L, 240_000L, 500_000L, 3_000L, null, true));

        var result = service.simulate(MEMBER_ID, PLAN_ID, requestWithoutAncillaryCosts());

        assertThat(result.initialCost().brokerageFee()).isEqualTo(880_000L);
        assertThat(result.initialCost().stampTax()).isEqualTo(70_000L);
        assertThat(result.initialCost().guaranteeFee()).isEqualTo(240_000L);
        assertThat(result.initialCost().movingCost()).isEqualTo(500_000L);
        // 보증금 1억 + 부대비용 1,690,000 + 예비비 3,000,000
        assertThat(result.initialCost().totalRequired()).isEqualTo(104_690_000L);
        assertThat(result.warnings()).contains(DiagnosisWarning.ANCILLARY_COST_ESTIMATED);
    }

    /** 사용자가 준 값이 있으면 그것이 먼저다. 서버가 굳이 계산하지 않는다. */
    @Test
    void should_useRequestedCosts_whenAllOfThemArePresent() {
        givenPlanAndInput(input(100_000_000L, 3_000_000L, 20_000_000L, 5_000_000L, true));

        var result = service.simulate(MEMBER_ID, PLAN_ID, request(200_000L));

        assertThat(result.initialCost().brokerageFee()).isEqualTo(500_000L);
        assertThat(result.warnings()).doesNotContain(DiagnosisWarning.ANCILLARY_COST_ESTIMATED);
        verify(ancillaryCosts, never())
                .ancillaryCost(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    /** 요율표를 확보하지 못한 구간(보증금 6억 초과)이다. 지어내지 않고 빠졌다고 말한다. */
    @Test
    void should_warnBrokerageUnknown_whenRateTableDoesNotCoverDeposit() {
        givenPlanAndInput(input(100_000_000L, 3_000_000L, 20_000_000L, 5_000_000L, true));
        when(ancillaryCosts.ancillaryCost(100_000_000L, 80_000_000L, null, null, null))
                .thenReturn(new AncillaryCostResponse(null, 70_000L, 240_000L, 500_000L, 3_000L, null, true));

        var result = service.simulate(MEMBER_ID, PLAN_ID, requestWithoutAncillaryCosts());

        assertThat(result.initialCost().brokerageFee()).isZero();
        assertThat(result.warnings()).contains(DiagnosisWarning.BROKERAGE_FEE_UNKNOWN);
    }

    /**
     * 생활비를 모르면 월 지출이 통째로 빠져 여유자금이 소득 전액에 가까워진다. 저축 가능액과
     * 독립 가능 시점이 실제보다 낙관적으로 나오므로, 0 으로 두더라도 모른다는 것을 남긴다.
     */
    @Test
    void should_warnMissingLivingExpenseAndReserve_whenRequestOmitsThem() {
        givenPlanAndInput(input(100_000_000L, 3_000_000L, 20_000_000L, 5_000_000L, true));

        var request = new DiagnosisCalculationRequest(
                1_000_000L, 500_000L, 300_000L, 75_000L, null, null, 200_000L, 80_000_000L, 200_000L);
        var result = service.simulate(MEMBER_ID, PLAN_ID, request);

        assertThat(result.initialCost().emergencyReserve()).isZero();
        assertThat(result.monthlyLivingExpense()).isZero();
        // 소득 3,000,000 − 주거비 300,000 − 상환 200,000. 생활비 900,000 이 빠진 만큼 크다.
        assertThat(result.monthlyDisposable()).isEqualTo(2_500_000L);
        assertThat(result.warnings())
                .contains(DiagnosisWarning.MONTHLY_LIVING_EXPENSE_MISSING, DiagnosisWarning.EMERGENCY_RESERVE_MISSING);
    }

    private DiagnosisCalculationRequest requestWithoutAncillaryCosts() {
        return new DiagnosisCalculationRequest(
                null, null, null, null, 3_000_000L, 900_000L, 200_000L, 80_000_000L, 200_000L);
    }

    private DiagnosisCalculationRequest request(long debtPayment) {
        return new DiagnosisCalculationRequest(
                1_000_000L, 500_000L, 300_000L, 75_000L, 3_000_000L, 900_000L, debtPayment, 80_000_000L, 200_000L);
    }
}
