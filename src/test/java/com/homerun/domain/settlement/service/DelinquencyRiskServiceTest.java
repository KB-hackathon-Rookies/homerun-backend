package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.settlement.dto.response.CashFlowSummaryResponse;
import com.homerun.domain.settlement.dto.response.DelinquencyRiskResponse;
import com.homerun.domain.settlement.dto.response.MonthlyMetricsResponse;
import com.homerun.domain.settlement.entity.FixedExpense;
import com.homerun.domain.settlement.repository.FixedExpenseRepository;
import com.homerun.domain.settlement.type.DelinquencyStatus;
import com.homerun.domain.settlement.type.ExpenseCategory;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 적자면 위험, 이자 미등록이면 판단불가, 이자 자동이체 미등록이면 경고를 본다. */
class DelinquencyRiskServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final CashFlowSummaryService cashFlow = mock(CashFlowSummaryService.class);
    private final FixedExpenseRepository expenses = mock(FixedExpenseRepository.class);
    private final DelinquencyRiskService service = new DelinquencyRiskService(cashFlow, expenses);

    private void givenRemaining(long remaining) {
        when(cashFlow.forPlan(MEMBER_ID, PLAN_ID))
                .thenReturn(new CashFlowSummaryResponse(0L, 0L, 0L, 0L, new MonthlyMetricsResponse(0L, 0L, remaining)));
    }

    private FixedExpense interest(boolean autopay) {
        return new FixedExpense(MEMBER_ID, PLAN_ID, "대출이자", ExpenseCategory.INTEREST, 264_000L, 25, autopay);
    }

    private void givenExpenses(FixedExpense... items) {
        when(expenses.findAllByPlanIdAndActiveTrueOrderByIdAsc(PLAN_ID)).thenReturn(List.of(items));
    }

    @Test
    void should_needFixedExpense_whenNoInterestRegistered() {
        givenRemaining(500_000L);
        givenExpenses(new FixedExpense(MEMBER_ID, PLAN_ID, "관리비", ExpenseCategory.MGMT, 50_000L, null, true));

        assertThat(service.forPlan(MEMBER_ID, PLAN_ID).status()).isEqualTo(DelinquencyStatus.NEEDS_FIXED_EXPENSE);
    }

    @Test
    void should_beAtRisk_whenRemainingIsNegative() {
        givenRemaining(-100_000L);
        givenExpenses(interest(true));

        DelinquencyRiskResponse r = service.forPlan(MEMBER_ID, PLAN_ID);
        assertThat(r.status()).isEqualTo(DelinquencyStatus.AT_RISK);
        assertThat(r.message()).contains("연체이자");
    }

    @Test
    void should_beOk_whenRemainingNonNegativeAndAutopayOn() {
        givenRemaining(0L); // 0 은 적자가 아니다 -- 경계
        givenExpenses(interest(true));

        DelinquencyRiskResponse r = service.forPlan(MEMBER_ID, PLAN_ID);
        assertThat(r.status()).isEqualTo(DelinquencyStatus.OK);
        assertThat(r.autopayWarning()).isFalse();
    }

    @Test
    void should_warnAutopay_whenInterestNotOnAutopay() {
        givenRemaining(500_000L);
        givenExpenses(interest(false));

        DelinquencyRiskResponse r = service.forPlan(MEMBER_ID, PLAN_ID);
        assertThat(r.status()).isEqualTo(DelinquencyStatus.OK);
        assertThat(r.autopayWarning()).isTrue();
    }
}
