package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.diagnosis.entity.Diagnosis;
import com.homerun.domain.diagnosis.repository.DiagnosisRepository;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.settlement.dto.response.CashFlowSummaryResponse;
import com.homerun.domain.settlement.entity.FixedExpense;
import com.homerun.domain.settlement.entity.LoanAccount;
import com.homerun.domain.settlement.repository.FixedExpenseRepository;
import com.homerun.domain.settlement.repository.LoanAccountRepository;
import com.homerun.domain.settlement.type.ExpenseCategory;
import com.homerun.domain.settlement.type.RepaymentType;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 저장 소스 조립을 본다: 관리비는 MGMT 합계만, 소득은 plan_input, 생활비는 최근 진단. */
class CashFlowSummaryServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final PlanInputRepository inputs = mock(PlanInputRepository.class);
    private final LoanAccountRepository loans = mock(LoanAccountRepository.class);
    private final FixedExpenseRepository expenses = mock(FixedExpenseRepository.class);
    private final DiagnosisRepository diagnoses = mock(DiagnosisRepository.class);
    // 계산은 진짜 계산기로 -- 조립부터 지표까지 한 번에 검증한다.
    private final CashFlowSummaryService service =
            new CashFlowSummaryService(plans, inputs, loans, expenses, diagnoses, new MonthlyMetricsCalculator());

    private void ownedPlan() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
    }

    private LoanAccount loan() {
        LoanAccount loan = new LoanAccount(PLAN_ID);
        loan.apply(
                ConsultedLoanProduct.YOUTH_BEOTIMMOK,
                null,
                144_000_000L,
                new BigDecimal("2.2"),
                RepaymentType.MATURITY_LUMP_SUM,
                LocalDate.of(2026, 5, 1),
                null,
                null,
                0);
        return loan;
    }

    private FixedExpense expense(ExpenseCategory category, long amount) {
        return new FixedExpense(MEMBER_ID, PLAN_ID, category.name(), category, amount, null, false);
    }

    @Test
    void should_assembleFromStoredSources() {
        ownedPlan();
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.of(loan()));
        // 관리비는 MGMT 합계만: 이자·기타는 관리비에 넣지 않는다.
        when(expenses.findAllByPlanIdAndActiveTrueOrderByIdAsc(PLAN_ID))
                .thenReturn(List.of(
                        expense(ExpenseCategory.MGMT, 50_000L),
                        expense(ExpenseCategory.INTEREST, 264_000L),
                        expense(ExpenseCategory.OTHER, 100_000L)));
        when(inputs.findByPlanId(PLAN_ID))
                .thenReturn(Optional.of(PlanInput.createWithOpenBankingIncome(PLAN_ID, 2_450_000L)));
        Diagnosis diagnosis = mock(Diagnosis.class);
        when(diagnosis.getMonthlyLivingExpense()).thenReturn(1_000_000L);
        when(diagnoses.findFirstByPlanIdAndCostEstimateIdIsNotNullOrderByCreatedAtDescIdDesc(PLAN_ID))
                .thenReturn(Optional.of(diagnosis));

        CashFlowSummaryResponse r = service.forPlan(MEMBER_ID, PLAN_ID);

        assertThat(r.managementFee()).isEqualTo(50_000L); // MGMT 만
        assertThat(r.monthlyIncome()).isEqualTo(2_450_000L);
        assertThat(r.livingCost()).isEqualTo(1_000_000L);
        assertThat(r.metrics().monthlyInterest()).isEqualTo(264_000L); // 대출에서 계산
        assertThat(r.metrics().housingCost()).isEqualTo(314_000L); // 관리비 5만 + 이자 26.4만
        assertThat(r.metrics().rirPercent()).isEqualByComparingTo("12.8");
    }

    @Test
    void should_fallBackToZero_whenNoInputOrDiagnosis() {
        ownedPlan();
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.of(loan()));
        when(expenses.findAllByPlanIdAndActiveTrueOrderByIdAsc(PLAN_ID)).thenReturn(List.of());
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
        when(diagnoses.findFirstByPlanIdAndCostEstimateIdIsNotNullOrderByCreatedAtDescIdDesc(PLAN_ID))
                .thenReturn(Optional.empty());

        CashFlowSummaryResponse r = service.forPlan(MEMBER_ID, PLAN_ID);

        assertThat(r.monthlyIncome()).isZero();
        assertThat(r.livingCost()).isZero();
        assertThat(r.managementFee()).isZero();
        assertThat(r.metrics().rirPercent()).isNull(); // 소득 0 이면 RIR 미산출
    }

    @Test
    void should_throw_whenNoLoanRegistered() {
        ownedPlan();
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forPlan(MEMBER_ID, PLAN_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.LOAN_ACCOUNT_NOT_FOUND));
    }

    @Test
    void should_throw_whenNotOwner() {
        ownedPlan();
        assertThatThrownBy(() -> service.forPlan(999L, PLAN_ID)).isInstanceOf(BusinessException.class);
    }
}
