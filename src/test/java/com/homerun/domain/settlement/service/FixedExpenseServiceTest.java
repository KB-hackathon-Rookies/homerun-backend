package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.settlement.dto.response.FixedExpenseListResponse;
import com.homerun.domain.settlement.entity.FixedExpense;
import com.homerun.domain.settlement.repository.FixedExpenseRepository;
import com.homerun.domain.settlement.type.ExpenseCategory;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 목록의 월 합계·연체 알림 활성(이자 항목 존재)과 삭제 소유권을 본다. */
class FixedExpenseServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final FixedExpenseRepository expenses = mock(FixedExpenseRepository.class);
    private final FixedExpenseService service = new FixedExpenseService(plans, expenses);

    private void ownedPlan() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
    }

    private FixedExpense expense(ExpenseCategory category, long amount) {
        return new FixedExpense(MEMBER_ID, PLAN_ID, category.name(), category, amount, null, false);
    }

    @Test
    void should_activateAlert_andSum_whenInterestRegistered() {
        ownedPlan();
        when(expenses.findAllByPlanIdAndActiveTrueOrderByIdAsc(PLAN_ID))
                .thenReturn(
                        List.of(expense(ExpenseCategory.INTEREST, 264_000L), expense(ExpenseCategory.MGMT, 50_000L)));

        FixedExpenseListResponse r = service.list(MEMBER_ID, PLAN_ID);

        assertThat(r.monthlyTotal()).isEqualTo(314_000L);
        assertThat(r.delinquencyAlertActive()).isTrue();
    }

    @Test
    void should_deactivateAlert_whenNoInterestExpense() {
        // 이자 항목이 없으면 연체 알림이 동작하지 않는다(FR-H5-01).
        ownedPlan();
        when(expenses.findAllByPlanIdAndActiveTrueOrderByIdAsc(PLAN_ID))
                .thenReturn(List.of(expense(ExpenseCategory.MGMT, 50_000L)));

        assertThat(service.list(MEMBER_ID, PLAN_ID).delinquencyAlertActive()).isFalse();
    }

    @Test
    void should_deactivateAlert_whenEmpty() {
        ownedPlan();
        when(expenses.findAllByPlanIdAndActiveTrueOrderByIdAsc(PLAN_ID)).thenReturn(List.of());

        FixedExpenseListResponse r = service.list(MEMBER_ID, PLAN_ID);
        assertThat(r.delinquencyAlertActive()).isFalse();
        assertThat(r.monthlyTotal()).isZero();
    }

    @Test
    void should_deleteOwnedExpense() {
        ownedPlan();
        FixedExpense e = expense(ExpenseCategory.MGMT, 50_000L);
        when(expenses.findByIdAndPlanId(5L, PLAN_ID)).thenReturn(Optional.of(e));

        service.delete(MEMBER_ID, PLAN_ID, 5L);

        verify(expenses).delete(e);
    }

    @Test
    void should_throw_whenDeletingMissingExpense() {
        ownedPlan();
        when(expenses.findByIdAndPlanId(5L, PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(MEMBER_ID, PLAN_ID, 5L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.FIXED_EXPENSE_NOT_FOUND));
    }

    @Test
    void should_throw_whenNotOwner() {
        ownedPlan();
        assertThatThrownBy(() -> service.list(999L, PLAN_ID)).isInstanceOf(BusinessException.class);
    }
}
