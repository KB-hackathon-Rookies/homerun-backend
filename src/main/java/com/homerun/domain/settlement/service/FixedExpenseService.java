package com.homerun.domain.settlement.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.settlement.dto.request.FixedExpenseRequest;
import com.homerun.domain.settlement.dto.response.FixedExpenseListResponse;
import com.homerun.domain.settlement.dto.response.FixedExpenseResponse;
import com.homerun.domain.settlement.entity.FixedExpense;
import com.homerun.domain.settlement.repository.FixedExpenseRepository;
import com.homerun.domain.settlement.type.ExpenseCategory;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 고정지출 등록·조회·삭제(FR-H5-01·02). */
@Service
public class FixedExpenseService {

    private final PlanRepository plans;
    private final FixedExpenseRepository expenses;

    public FixedExpenseService(PlanRepository plans, FixedExpenseRepository expenses) {
        this.plans = plans;
        this.expenses = expenses;
    }

    @Transactional
    public FixedExpenseResponse add(Long memberId, Long planId, FixedExpenseRequest request) {
        ownedPlan(memberId, planId);
        FixedExpense saved = expenses.save(new FixedExpense(
                memberId,
                planId,
                request.name(),
                request.category(),
                request.amount(),
                request.dueDay(),
                request.autopay()));
        return FixedExpenseResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public FixedExpenseListResponse list(Long memberId, Long planId) {
        ownedPlan(memberId, planId);
        List<FixedExpense> found = expenses.findAllByPlanIdAndActiveTrueOrderByIdAsc(planId);
        List<FixedExpenseResponse> items =
                found.stream().map(FixedExpenseResponse::from).toList();
        long total = found.stream().mapToLong(FixedExpense::getAmount).sum();
        // 이자 항목이 없으면 연체 알림이 동작하지 않는다(FR-H5-01).
        boolean alertActive = found.stream().anyMatch(e -> e.getCategory() == ExpenseCategory.INTEREST);
        return new FixedExpenseListResponse(items, total, alertActive);
    }

    @Transactional
    public void delete(Long memberId, Long planId, Long expenseId) {
        ownedPlan(memberId, planId);
        FixedExpense expense = expenses.findByIdAndPlanId(expenseId, planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FIXED_EXPENSE_NOT_FOUND));
        expenses.delete(expense);
    }

    private void ownedPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
    }
}
