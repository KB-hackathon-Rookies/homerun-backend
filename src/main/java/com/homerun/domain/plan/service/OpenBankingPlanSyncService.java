package com.homerun.domain.plan.service;

import com.homerun.domain.openbanking.dto.response.OpenBankingFinancialSummaryResponse;
import com.homerun.domain.openbanking.service.OpenBankingService;
import com.homerun.domain.plan.dto.response.PlanFinancialSyncResponse;
import com.homerun.domain.plan.service.PlanInputService.OpenBankingIncomeSyncResult;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OpenBankingPlanSyncService {
    private static final int REQUIRED_SALARY_MONTHS = 3;
    private final PlanInputService inputs;
    private final OpenBankingService openBanking;

    public OpenBankingPlanSyncService(PlanInputService inputs, OpenBankingService openBanking) {
        this.inputs = inputs;
        this.openBanking = openBanking;
    }

    public PlanFinancialSyncResponse sync(Long memberId, Long planId, List<String> bankCodes) {
        inputs.verifyOwner(memberId, planId);
        OpenBankingFinancialSummaryResponse summary = openBanking.financialSummary(memberId, bankCodes);
        Long income = eligibleIncome(summary);
        OpenBankingIncomeSyncResult result = inputs.syncOpenBankingIncome(memberId, planId, income);
        return new PlanFinancialSyncResponse(
                planId,
                summary.status(),
                summary.accountBalanceCoverage(),
                summary.accountTransactionCoverage(),
                income,
                summary.salaryDetectedMonths(),
                result.status(),
                summary.totalAvailableBalance(),
                null,
                summary.averageMonthlyLoanRepayment(),
                summary.loanCount(),
                summary.incomplete(),
                summary.warnings(),
                summary.fetchedAt(),
                result.input());
    }

    Long eligibleIncome(OpenBankingFinancialSummaryResponse summary) {
        BigDecimal value = summary.averageMonthlyNetIncome();
        if (!summary.accountTransactionCoverage().complete()
                || summary.accountTransactionCoverage().requested() == 0
                || summary.salaryDetectedMonths() != REQUIRED_SALARY_MONTHS
                || value == null
                || value.signum() < 0) return null;
        try {
            return value.longValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }
}
