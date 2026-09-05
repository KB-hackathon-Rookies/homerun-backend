package com.homerun.domain.alternative.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.homerun.domain.alternative.dto.request.AlternativeRecalculationRequest;
import com.homerun.domain.diagnosis.dto.request.DiagnosisCalculationRequest;
import com.homerun.domain.diagnosis.repository.CostEstimateRepository;
import com.homerun.domain.diagnosis.repository.DiagnosisRepository;
import com.homerun.domain.diagnosis.service.DiagnosisService;
import com.homerun.domain.openbanking.repository.FinancialSnapshotRepository;
import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.FinancialValueSource;
import com.homerun.domain.plan.type.LeaseType;
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

/**
 * 진단 공식 자체는 DiagnosisServiceTest 가 본다. 여기서는 대안 재계산이 <b>계산을 다시 하지 않고</b>
 * 축만 바꿔 두 번 부르는지, 그리고 델타를 정직하게 내는지만 본다.
 *
 * <p>진짜 DiagnosisService 를 붙인다 — 목으로 감싸면 "축을 바꾸면 결과가 달라진다"를 스텁이
 * 대신 말해 주는 셈이라 아무것도 검증하지 못한다.
 */
@ExtendWith(MockitoExtension.class)
class AlternativeRecalculationServiceTest {

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

    private AlternativeRecalculationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);
        service = new AlternativeRecalculationService(
                new DiagnosisService(plans, inputs, snapshots, costs, diagnoses, clock));
    }

    @Test
    void should_lowerShortfall_when_alternativeDepositIsSmaller() {
        givenPlanAndInput();

        // 보증금 1억 → 8천만. 같은 비용 가정이라 차이는 보증금 하나뿐이다.
        var response = service.recalculate(MEMBER_ID, PLAN_ID, request(80_000_000L, null));

        assertThat(response.alternative().initialCost().deposit()).isEqualTo(80_000_000L);
        assertThat(response.current().initialCost().deposit()).isEqualTo(100_000_000L);
        assertThat(response.shortfallChange()).isNegative();
        assertThat(response.alternative().shortfall())
                .isEqualTo(response.current().shortfall() + response.shortfallChange());
    }

    @Test
    void should_giveMoreMonthsToSave_when_alternativeMoveDateIsLater() {
        givenPlanAndInput();

        // 계획의 목표 이사일은 6개월 뒤. 12개월 뒤로 미뤄 보면 모을 수 있는 달이 늘어난다.
        var response = service.recalculate(MEMBER_ID, PLAN_ID, request(null, TODAY.plusMonths(12)));

        assertThat(response.current().monthsToMove()).isEqualTo(6);
        assertThat(response.alternative().monthsToMove()).isEqualTo(12);
        assertThat(response.alternative().savableAmount())
                .isGreaterThan(response.current().savableAmount());
    }

    @Test
    void should_returnIdenticalSides_when_noAxisIsOverridden() {
        givenPlanAndInput();

        var response = service.recalculate(MEMBER_ID, PLAN_ID, request(null, null));

        assertThat(response.shortfallChange()).isZero();
        assertThat(response.possibleDateShiftDays()).isZero();
        assertThat(response.alternative().verdict())
                .isEqualTo(response.current().verdict());
    }

    @Test
    void should_notPersistAnything_becauseRecalculationOnlyExplores() {
        givenPlanAndInput();

        service.recalculate(MEMBER_ID, PLAN_ID, request(80_000_000L, null));

        // 저장이 한 번이라도 일어나면 대안을 눌러 본 것만으로 계획 기록이 바뀐다.
        org.mockito.Mockito.verifyNoInteractions(costs, diagnoses);
    }

    @Test
    void should_reportNullShift_when_oneSideCannotReachTheGoal() {
        // 월 가처분이 음수라 자금 확보 시점 자체가 안 나온다. 0일로 채우면 "변화 없음"으로 읽힌다.
        givenPlanAndInput(input(100_000_000L, 1_000_000L, 1_000_000L));

        var response = service.recalculate(MEMBER_ID, PLAN_ID, request(80_000_000L, null));

        assertThat(response.current().possibleDate()).isNull();
        assertThat(response.possibleDateShiftDays()).isNull();
    }

    /** 사용 가능 현금을 일부러 부족하게 잡는다 — 부족자금이 0이면 보증금을 낮춰도 줄어들 것이 없어
     * 정렬·델타를 검증할 수 없다. */
    private void givenPlanAndInput() {
        givenPlanAndInput(input(100_000_000L, 3_000_000L, 5_000_000L));
    }

    private void givenPlanAndInput(PlanInput input) {
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, TODAY.plusMonths(6))));
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));
    }

    private PlanInput input(Long deposit, long income, long availableCash) {
        return PlanInput.create(
                PLAN_ID,
                new PlanInputRequest(
                        deposit,
                        0L,
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
                        FinancialValueSource.MANUAL,
                        null,
                        true,
                        Set.of()));
    }

    private AlternativeRecalculationRequest request(Long hopeDeposit, LocalDate targetMoveDate) {
        DiagnosisCalculationRequest assumptions = new DiagnosisCalculationRequest(
                1_000_000L, 500_000L, 300_000L, 75_000L, 3_000_000L, 900_000L, 200_000L, 80_000_000L, 200_000L);
        return new AlternativeRecalculationRequest(assumptions, hopeDeposit, targetMoveDate);
    }
}
