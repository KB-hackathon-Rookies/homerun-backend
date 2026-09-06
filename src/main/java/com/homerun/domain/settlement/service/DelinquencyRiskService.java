package com.homerun.domain.settlement.service;

import com.homerun.domain.settlement.dto.response.CashFlowSummaryResponse;
import com.homerun.domain.settlement.dto.response.DelinquencyRiskResponse;
import com.homerun.domain.settlement.entity.FixedExpense;
import com.homerun.domain.settlement.repository.FixedExpenseRepository;
import com.homerun.domain.settlement.type.DelinquencyStatus;
import com.homerun.domain.settlement.type.ExpenseCategory;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연체 위험 판단(FR-H5-04). 월 잔여금이 적자면 위험으로 본다. 소유권 확인·현금흐름 계산은
 * {@link CashFlowSummaryService} 를 그대로 재사용한다.
 *
 * <p>RIR 컷오프(O-3 미결)는 쓰지 않는다. 적자(잔여금 &lt; 0)라는 명확한 선만 쓴다. 이자 고정지출이
 * 없으면 판단 자체가 안 되므로 등록을 안내한다(FR-H5-01).
 */
@Service
public class DelinquencyRiskService {

    private final CashFlowSummaryService cashFlow;
    private final FixedExpenseRepository expenses;

    public DelinquencyRiskService(CashFlowSummaryService cashFlow, FixedExpenseRepository expenses) {
        this.cashFlow = cashFlow;
        this.expenses = expenses;
    }

    @Transactional(readOnly = true)
    public DelinquencyRiskResponse forPlan(Long memberId, Long planId) {
        // 소유권·대출 확인과 현금흐름 계산을 재사용한다.
        CashFlowSummaryResponse flow = cashFlow.forPlan(memberId, planId);
        long remaining = flow.metrics().remaining();

        List<FixedExpense> interest = expenses.findAllByPlanIdAndActiveTrueOrderByIdAsc(planId).stream()
                .filter(e -> e.getCategory() == ExpenseCategory.INTEREST)
                .toList();

        if (interest.isEmpty()) {
            // 이자 항목이 없으면 연체 판단이 동작하지 않는다(FR-H5-01).
            return new DelinquencyRiskResponse(
                    DelinquencyStatus.NEEDS_FIXED_EXPENSE, remaining, false, "대출 이자를 고정지출로 등록해야 연체 위험을 알려드릴 수 있어요.");
        }

        boolean autopayWarning = interest.stream().anyMatch(e -> !e.isAutopay());
        if (remaining < 0) {
            return new DelinquencyRiskResponse(
                    DelinquencyStatus.AT_RISK,
                    remaining,
                    autopayWarning,
                    "이번 달 잔여금이 마이너스예요. 상환일 전에 자금을 준비하세요. 연체 시 연체이자(+4~5%p)가 붙고,"
                            + " 3개월을 넘기면 기한이익상실로 잔액 전체를 갚아야 할 수 있어요.");
        }
        String message =
                autopayWarning ? "잔여금은 플러스예요. 다만 이자 자동이체가 등록되지 않았으니 상환일을 놓치지 않게 등록하세요." : "잔여금이 플러스라 상환 여력이 있어요.";
        return new DelinquencyRiskResponse(DelinquencyStatus.OK, remaining, autopayWarning, message);
    }
}
