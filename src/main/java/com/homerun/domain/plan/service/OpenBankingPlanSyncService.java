package com.homerun.domain.plan.service;

import com.homerun.domain.openbanking.dto.response.FinancialSnapshotResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingFinancialSummaryResponse;
import com.homerun.domain.openbanking.service.FinancialSnapshotService;
import com.homerun.domain.openbanking.service.OpenBankingService;
import com.homerun.domain.openbanking.type.Persona;
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
    private final FinancialSnapshotService snapshots;

    public OpenBankingPlanSyncService(
            PlanInputService inputs, OpenBankingService openBanking, FinancialSnapshotService snapshots) {
        this.inputs = inputs;
        this.openBanking = openBanking;
        this.snapshots = snapshots;
    }

    /**
     * 데모용 가짜 오픈뱅킹 연동. 실연동 불가로, 선택한 페르소나 값을 스냅샷(자산확인 화면용)과
     * 계획 입력(판정용)에 함께 적재한다. 순자산은 금융자산 − 대출잔액으로 둔다.
     */
    public FinancialSnapshotResponse mockConnect(Long memberId, Long planId, Persona persona) {
        inputs.verifyOwner(memberId, planId);
        FinancialSnapshotResponse snapshot = snapshots.connectMock(memberId, persona);
        // 순자산은 음수를 허용하지 않는다(ck_plan_input_net_assets) — 부채가 자산보다 크면 0으로 둔다.
        long netAssets = Math.max(0L, persona.getFinancialAsset() - persona.getLoanBalance());
        inputs.applyMockFinancials(memberId, planId, persona.getMonthlyIncome(), netAssets);
        return snapshot;
    }

    public PlanFinancialSyncResponse sync(Long memberId, Long planId, List<String> bankCodes) {
        inputs.verifyOwner(memberId, planId);
        OpenBankingFinancialSummaryResponse summary = openBanking.financialSummary(memberId, bankCodes);
        Long income = eligibleIncome(summary);
        FinancialSnapshotResponse snapshot = snapshots.capture(memberId, summary, income);
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
                snapshot,
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
