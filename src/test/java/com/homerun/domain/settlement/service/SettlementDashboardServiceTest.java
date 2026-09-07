package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.settlement.dto.response.DashboardItemResponse;
import com.homerun.domain.settlement.dto.response.SettlementDashboardResponse;
import com.homerun.domain.settlement.entity.LoanAccount;
import com.homerun.domain.settlement.entity.ReturnGuaranteeEnrollment;
import com.homerun.domain.settlement.repository.LoanAccountRepository;
import com.homerun.domain.settlement.repository.ReturnGuaranteeEnrollmentRepository;
import com.homerun.domain.settlement.type.DashboardItemStatus;
import com.homerun.domain.settlement.type.RepaymentType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 확인 가능한 항목만 DONE/PENDING, 미저장 항목은 NOT_TRACKED, 진행률은 NOT_TRACKED 제외를 본다. */
class SettlementDashboardServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneOffset.UTC);

    private final PlanRepository plans = mock(PlanRepository.class);
    private final LeaseContractRepository contracts = mock(LeaseContractRepository.class);
    private final LoanAccountRepository loans = mock(LoanAccountRepository.class);
    private final ReturnGuaranteeStatusResolver returnGuaranteeResolver = mock(ReturnGuaranteeStatusResolver.class);
    private final ReturnGuaranteeEnrollmentRepository enrollments = mock(ReturnGuaranteeEnrollmentRepository.class);
    private final SettlementDashboardService service =
            new SettlementDashboardService(plans, contracts, loans, returnGuaranteeResolver, enrollments, CLOCK);

    private void ownedPlan() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        // 기본: 반환보증 없음, 가입 기록 없음. HUG·가입 케이스는 개별 테스트에서 덮어쓴다.
        when(enrollments.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
    }

    private LoanAccount loan(CollateralMethod guarantee) {
        LoanAccount l = new LoanAccount(PLAN_ID);
        l.apply(
                ConsultedLoanProduct.YOUTH_BEOTIMMOK,
                guarantee,
                144_000_000L,
                new BigDecimal("2.2"),
                RepaymentType.MATURITY_LUMP_SUM,
                LocalDate.of(2026, 5, 1),
                null,
                null,
                0);
        return l;
    }

    private LeaseContract contract(LocalDate moveInReport, LocalDate confirmed) {
        LeaseContract c = mock(LeaseContract.class);
        when(c.getMoveInReportAt()).thenReturn(moveInReport);
        when(c.getConfirmedDateAt()).thenReturn(confirmed);
        return c;
    }

    private DashboardItemStatus statusOf(SettlementDashboardResponse r, String code) {
        return r.items().stream()
                .filter(i -> i.code().equals(code))
                .map(DashboardItemResponse::status)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void should_markDoneAndComputeProgressAndDPlusN_whenSettled() {
        ownedPlan();
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.of(loan(CollateralMethod.HUG_SAFE_JEONSE)));
        when(returnGuaranteeResolver.hasReturnGuarantee(PLAN_ID)).thenReturn(true);
        LeaseContract c = contract(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1));
        when(contracts.findByPlanId(PLAN_ID)).thenReturn(Optional.of(c));

        SettlementDashboardResponse r = service.forPlan(MEMBER_ID, PLAN_ID);

        assertThat(statusOf(r, "LOAN_EXECUTED")).isEqualTo(DashboardItemStatus.DONE);
        assertThat(statusOf(r, "MOVE_IN_REPORT")).isEqualTo(DashboardItemStatus.DONE);
        assertThat(statusOf(r, "CONFIRMED_DATE")).isEqualTo(DashboardItemStatus.DONE);
        assertThat(statusOf(r, "RETURN_GUARANTEE")).isEqualTo(DashboardItemStatus.DONE); // HUG 포함
        assertThat(statusOf(r, "YEAR_END_TAX")).isEqualTo(DashboardItemStatus.NOT_TRACKED);
        // 추적 4개 전부 DONE → 100%. NOT_TRACKED 2개는 분모에서 제외.
        assertThat(r.progressPercent()).isEqualTo(100);
        // D+N: 2026-09-01 전입 → 오늘(09-30) 29일.
        assertThat(r.daysSinceIndependence()).isEqualTo(29);
    }

    @Test
    void should_markPending_andNullDPlusN_whenNothingDone() {
        ownedPlan();
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
        when(contracts.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        SettlementDashboardResponse r = service.forPlan(MEMBER_ID, PLAN_ID);

        assertThat(statusOf(r, "LOAN_EXECUTED")).isEqualTo(DashboardItemStatus.PENDING);
        assertThat(statusOf(r, "MOVE_IN_REPORT")).isEqualTo(DashboardItemStatus.PENDING);
        assertThat(statusOf(r, "RETURN_GUARANTEE")).isEqualTo(DashboardItemStatus.NOT_TRACKED);
        // 추적 3개(LOAN·전입·확정) 전부 미완료 → 0%.
        assertThat(r.progressPercent()).isZero();
        assertThat(r.daysSinceIndependence()).isNull();
    }

    @Test
    void should_markPendingReturnGuarantee_whenEnrollmentRecordButNotEnrolled() {
        // 가입 기록은 있는데 아직 가입 전이면 해야 할 일(PENDING) -- 미추적(NOT_TRACKED)과 구분한다.
        ownedPlan();
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.of(loan(CollateralMethod.HF)));
        when(contracts.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
        ReturnGuaranteeEnrollment notYet = new ReturnGuaranteeEnrollment(PLAN_ID);
        notYet.update(false, false, null);
        when(enrollments.findByPlanId(PLAN_ID)).thenReturn(Optional.of(notYet));

        SettlementDashboardResponse r = service.forPlan(MEMBER_ID, PLAN_ID);
        assertThat(statusOf(r, "RETURN_GUARANTEE")).isEqualTo(DashboardItemStatus.PENDING);
    }

    @Test
    void should_notTrackReturnGuarantee_whenNotHug() {
        ownedPlan();
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.of(loan(CollateralMethod.HF)));
        when(contracts.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        SettlementDashboardResponse r = service.forPlan(MEMBER_ID, PLAN_ID);
        assertThat(statusOf(r, "RETURN_GUARANTEE")).isEqualTo(DashboardItemStatus.NOT_TRACKED);
    }

    @Test
    void should_excludeNotTrackedFromProgress() {
        // 대출만 실행(1/3 추적 항목 DONE) → 33%. NOT_TRACKED 3개가 분모에 들어가면 17%로 왜곡된다.
        ownedPlan();
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.of(loan(CollateralMethod.HF)));
        when(contracts.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        assertThat(service.forPlan(MEMBER_ID, PLAN_ID).progressPercent()).isEqualTo(33);
    }
}
