package com.homerun.domain.settlement.service;

import com.homerun.domain.diagnosis.repository.DiagnosisRepository;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.settlement.dto.response.CashFlowSummaryResponse;
import com.homerun.domain.settlement.entity.LoanAccount;
import com.homerun.domain.settlement.repository.FixedExpenseRepository;
import com.homerun.domain.settlement.repository.LoanAccountRepository;
import com.homerun.domain.settlement.type.ExpenseCategory;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현금흐름 종합(FR-H5-03). 저장된 대출·고정지출·소득·생활비를 모아 월간 지표를 계산한다.
 * 새 계산은 하지 않고 {@link MonthlyMetricsCalculator} 를 재사용한다.
 */
@Service
public class CashFlowSummaryService {

    private final PlanRepository plans;
    private final PlanInputRepository inputs;
    private final LoanAccountRepository loans;
    private final FixedExpenseRepository expenses;
    private final DiagnosisRepository diagnoses;
    private final MonthlyMetricsCalculator calculator;

    public CashFlowSummaryService(
            PlanRepository plans,
            PlanInputRepository inputs,
            LoanAccountRepository loans,
            FixedExpenseRepository expenses,
            DiagnosisRepository diagnoses,
            MonthlyMetricsCalculator calculator) {
        this.plans = plans;
        this.inputs = inputs;
        this.loans = loans;
        this.expenses = expenses;
        this.diagnoses = diagnoses;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public CashFlowSummaryResponse forPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        // 대출이 없으면 현금흐름을 계산할 수 없다 -- 대출부터 등록하라고 알린다.
        LoanAccount loan =
                loans.findByPlanId(planId).orElseThrow(() -> new BusinessException(ErrorCode.LOAN_ACCOUNT_NOT_FOUND));

        long managementFee = expenses.findAllByPlanIdAndActiveTrueOrderByIdAsc(planId).stream()
                .filter(e -> e.getCategory() == ExpenseCategory.MGMT)
                .mapToLong(e -> e.getAmount())
                .sum();
        long monthlyIncome = inputs.findByPlanId(planId)
                .map(input -> input.getMonthlyIncome())
                .filter(v -> v != null)
                .orElse(0L);
        long livingCost = diagnoses
                .findFirstByPlanIdAndCostEstimateIdIsNotNullOrderByCreatedAtDescIdDesc(planId)
                .map(d -> d.getMonthlyLivingExpense())
                .orElse(0L);

        return new CashFlowSummaryResponse(
                loan.getPrincipal(),
                monthlyIncome,
                managementFee,
                livingCost,
                calculator.calculate(loan.getPrincipal(), loan.getRate(), managementFee, monthlyIncome, livingCost));
    }
}
