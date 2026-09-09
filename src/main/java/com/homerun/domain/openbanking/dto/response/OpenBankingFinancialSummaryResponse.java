package com.homerun.domain.openbanking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record OpenBankingFinancialSummaryResponse(
        FinancialSummaryStatus status,
        ExternalDataCoverage accountBalanceCoverage,
        ExternalDataCoverage accountTransactionCoverage,
        ExternalDataCoverage loanInstitutionCoverage,
        int connectedAccountCount,
        BigDecimal totalAccountBalance,
        BigDecimal totalAvailableBalance,
        List<AccountBalanceBreakdownResponse> accountBalances,
        BigDecimal averageMonthlyNetIncome,
        int salaryDetectedMonths,
        List<MonthlyIncomeResponse> monthlyNetIncomes,
        BigDecimal averageMonthlyLoanRepayment,
        int loanCount,
        int loanRepaymentDetailUnavailableCount,
        boolean incomplete,
        LocalDate calculationFromDate,
        LocalDate calculationToDate,
        int calculationMonths,
        List<String> searchedLoanBankCodes,
        List<OpenBankingLoanResponse> loans,
        List<OpenBankingDataWarning> warnings,
        Instant fetchedAt) {}
