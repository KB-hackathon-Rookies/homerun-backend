package com.homerun.domain.openbanking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record OpenBankingFinancialSummaryResponse(
        int connectedAccountCount,
        BigDecimal totalAccountBalance,
        BigDecimal totalAvailableBalance,
        BigDecimal averageMonthlyNetIncome,
        int salaryDetectedMonths,
        BigDecimal averageMonthlyLoanRepayment,
        int loanCount,
        int loanRepaymentDetailUnavailableCount,
        boolean incomplete,
        LocalDate calculationFromDate,
        LocalDate calculationToDate,
        int calculationMonths,
        List<String> searchedLoanBankCodes,
        List<OpenBankingLoanResponse> loans,
        Instant fetchedAt) {}
