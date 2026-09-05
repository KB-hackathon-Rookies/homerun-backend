package com.homerun.domain.openbanking.service;

import com.homerun.domain.openbanking.dto.response.FinancialSnapshotResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingFinancialSummaryResponse;
import com.homerun.domain.openbanking.entity.FinancialSnapshot;
import com.homerun.domain.openbanking.repository.FinancialSnapshotRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialSnapshotService {

    private final FinancialSnapshotRepository snapshots;

    public FinancialSnapshotService(FinancialSnapshotRepository snapshots) {
        this.snapshots = snapshots;
    }

    @Transactional
    public FinancialSnapshotResponse capture(
            Long memberId, OpenBankingFinancialSummaryResponse summary, Long eligibleMonthlyIncome) {
        Long financialAsset = summary.accountBalanceCoverage().requested() > 0
                        && summary.accountBalanceCoverage().complete()
                ? exactLong(summary.totalAccountBalance())
                : null;
        Long monthlyDebtPayment = summary.loanInstitutionCoverage().requested() > 0
                        && summary.loanInstitutionCoverage().complete()
                        && summary.loanRepaymentDetailUnavailableCount() == 0
                ? exactLong(summary.averageMonthlyLoanRepayment())
                : null;
        if (financialAsset == null && eligibleMonthlyIncome == null && monthlyDebtPayment == null) {
            return null;
        }
        FinancialSnapshot snapshot = FinancialSnapshot.fromOpenBanking(
                memberId,
                summary.calculationToDate(),
                financialAsset,
                eligibleMonthlyIncome,
                monthlyDebtPayment,
                summary.fetchedAt());
        return FinancialSnapshotResponse.from(snapshots.save(snapshot));
    }

    @Transactional(readOnly = true)
    public FinancialSnapshotResponse latest(Long memberId) {
        return snapshots
                .findFirstByUserIdOrderByCreatedAtDescIdDesc(memberId)
                .map(FinancialSnapshotResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPEN_BANKING_FINANCIAL_SNAPSHOT_NOT_FOUND));
    }

    private Long exactLong(BigDecimal value) {
        if (value == null || value.signum() < 0) return null;
        try {
            return value.longValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }
}
