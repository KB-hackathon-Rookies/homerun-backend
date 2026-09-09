package com.homerun.domain.openbanking.service;

import com.homerun.domain.openbanking.dto.response.AccountBalanceBreakdownResponse;
import com.homerun.domain.openbanking.dto.response.ExternalDataCoverage;
import com.homerun.domain.openbanking.dto.response.FinancialSummaryStatus;
import com.homerun.domain.openbanking.dto.response.MonthlyIncomeResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingDataWarning;
import com.homerun.domain.openbanking.dto.response.OpenBankingDataWarning.Source;
import com.homerun.domain.openbanking.dto.response.OpenBankingFinancialSummaryResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingLoanResponse;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.openbanking.OpenBankingClient;
import com.homerun.global.external.openbanking.OpenBankingResponses.Account;
import com.homerun.global.external.openbanking.OpenBankingResponses.Balance;
import com.homerun.global.external.openbanking.OpenBankingResponses.Loan;
import com.homerun.global.external.openbanking.OpenBankingResponses.LoanBasicPage;
import com.homerun.global.external.openbanking.OpenBankingResponses.LoanPage;
import com.homerun.global.external.openbanking.OpenBankingResponses.Transaction;
import com.homerun.global.external.openbanking.OpenBankingResponses.TransactionPage;
import com.homerun.global.external.openbanking.OpenBankingResponses.UserInfo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OpenBankingFinancialSummaryAggregator {

    private static final int SUMMARY_MONTHS = 3;
    private static final int MAX_PAGES = 100;

    private final OpenBankingClient client;
    private final Clock clock;

    OpenBankingFinancialSummaryAggregator(OpenBankingClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    OpenBankingFinancialSummaryResponse aggregate(String accessToken, UserInfo userInfo, List<String> bankCodes) {
        SummaryPeriod period = summaryPeriod();
        List<OpenBankingDataWarning> warnings = new ArrayList<>();
        BigDecimal totalBalance = BigDecimal.ZERO;
        BigDecimal totalAvailableBalance = BigDecimal.ZERO;
        List<AccountBalanceBreakdownResponse> accountBalances = new ArrayList<>();
        Map<YearMonth, BigDecimal> monthlySalary = new LinkedHashMap<>();
        int balanceSucceeded = 0;
        int transactionsSucceeded = 0;

        for (Account account : userInfo.accounts()) {
            try {
                Balance balance = client.balance(accessToken, account.fintechUseNumber());
                totalBalance = totalBalance.add(orZero(balance.balanceAmount()));
                totalAvailableBalance = totalAvailableBalance.add(orZero(balance.availableAmount()));
                // 합계를 계좌별로 풀 수 있게 성공한 잔액을 그대로 담는다. 실패한 계좌는 합에도
                // 안 들어가므로 여기에도 넣지 않는다.
                accountBalances.add(new AccountBalanceBreakdownResponse(
                        bankNameOf(balance, account),
                        account.accountNumberMasked(),
                        balance.productName(),
                        account.accountType(),
                        orZero(balance.balanceAmount()),
                        orZero(balance.availableAmount())));
                balanceSucceeded++;
            } catch (BusinessException exception) {
                rethrowUnlessRecoverable(exception);
                warnings.add(warning(
                        Source.ACCOUNT_BALANCE,
                        account.accountNumberMasked(),
                        "BALANCE_UNAVAILABLE",
                        "일부 계좌의 잔액을 조회하지 못했습니다."));
            }

            try {
                for (Transaction transaction : fetchTransactions(accessToken, account, period)) {
                    if (isSalary(transaction)) {
                        YearMonth month = YearMonth.from(transaction.date());
                        monthlySalary.merge(month, transaction.amount().abs(), BigDecimal::add);
                    }
                }
                transactionsSucceeded++;
            } catch (BusinessException exception) {
                rethrowUnlessRecoverable(exception);
                warnings.add(warning(
                        Source.ACCOUNT_TRANSACTIONS,
                        account.accountNumberMasked(),
                        "TRANSACTIONS_UNAVAILABLE",
                        "일부 계좌의 거래내역을 조회하지 못했습니다."));
            }
        }

        LoanFetchResult loanResult = fetchLoans(accessToken, userInfo.userSeqNo(), bankCodes, warnings);
        int repaymentUnavailableCount = 0;
        int repaymentSucceeded = 0;
        BigDecimal repaymentTotal = BigDecimal.ZERO;
        for (Loan loan : loanResult.loans()) {
            if (isBlank(loan.accountNumber())) {
                repaymentUnavailableCount++;
                continue;
            }
            try {
                repaymentTotal =
                        repaymentTotal.add(fetchLoanRepaymentTotal(accessToken, userInfo.userSeqNo(), loan, period));
                repaymentSucceeded++;
            } catch (BusinessException exception) {
                rethrowUnlessRecoverable(exception);
                repaymentUnavailableCount++;
                warnings.add(warning(
                        Source.LOAN_REPAYMENT,
                        loan.accountNumberMasked(),
                        "REPAYMENT_UNAVAILABLE",
                        "일부 대출의 상환내역을 조회하지 못했습니다."));
            }
        }
        long missingAccountNumberCount = loanResult.loans().stream()
                .filter(loan -> isBlank(loan.accountNumber()))
                .count();
        if (missingAccountNumberCount > 0) {
            warnings.add(warning(
                    Source.LOAN_REPAYMENT,
                    null,
                    "LOAN_ACCOUNT_NUMBER_NOT_PROVIDED",
                    "이용기관 자격 제한으로 일부 대출의 상환내역을 조회할 수 없습니다."));
        }

        BigDecimal averageSalary = monthlySalary.isEmpty()
                ? null
                : divide(
                        monthlySalary.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add), monthlySalary.size());
        // 월별 소득 내역 — 어느 달을 몇 번 잡아 평균했는지 보이게 오름차순으로 편다.
        List<MonthlyIncomeResponse> monthlyNetIncomes = monthlySalary.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new MonthlyIncomeResponse(entry.getKey().toString(), entry.getValue()))
                .toList();
        BigDecimal averageRepayment = averageRepayment(loanResult, repaymentSucceeded, repaymentTotal);
        FinancialSummaryStatus status = status(
                warnings,
                userInfo.accounts().size() * 2 + bankCodes.size(),
                balanceSucceeded + transactionsSucceeded + loanResult.succeededBankCodes());

        return new OpenBankingFinancialSummaryResponse(
                status,
                new ExternalDataCoverage(userInfo.accounts().size(), balanceSucceeded),
                new ExternalDataCoverage(userInfo.accounts().size(), transactionsSucceeded),
                new ExternalDataCoverage(bankCodes.size(), loanResult.succeededBankCodes()),
                userInfo.accounts().size(),
                totalBalance,
                totalAvailableBalance,
                List.copyOf(accountBalances),
                averageSalary,
                monthlySalary.size(),
                monthlyNetIncomes,
                averageRepayment,
                loanResult.loans().size(),
                repaymentUnavailableCount,
                status != FinancialSummaryStatus.COMPLETE || repaymentUnavailableCount > 0,
                period.fromDate(),
                period.toDate(),
                SUMMARY_MONTHS,
                bankCodes,
                loanResult.loans().stream().map(OpenBankingLoanResponse::from).toList(),
                List.copyOf(warnings),
                Instant.now(clock));
    }

    private LoanFetchResult fetchLoans(
            String accessToken, String userSeqNo, List<String> bankCodes, List<OpenBankingDataWarning> warnings) {
        Map<String, Loan> uniqueLoans = new LinkedHashMap<>();
        int succeeded = 0;
        for (String bankCode : bankCodes) {
            try {
                fetchLoansForBank(accessToken, userSeqNo, bankCode)
                        .forEach(loan -> uniqueLoans.putIfAbsent(loanKey(loan), loan));
                succeeded++;
            } catch (BusinessException exception) {
                rethrowUnlessRecoverable(exception);
                warnings.add(
                        warning(Source.LOAN_LIST, bankCode, "LOAN_LIST_UNAVAILABLE", "일부 금융기관의 대출 목록을 조회하지 못했습니다."));
            }
        }
        return new LoanFetchResult(List.copyOf(uniqueLoans.values()), bankCodes.size(), succeeded);
    }

    private List<Loan> fetchLoansForBank(String accessToken, String userSeqNo, String bankCode) {
        List<Loan> result = new ArrayList<>();
        String traceInfo = null;
        for (int pageNumber = 0; pageNumber < MAX_PAGES; pageNumber++) {
            LoanPage page = client.loans(accessToken, userSeqNo, bankCode, traceInfo);
            result.addAll(page.loans());
            if (!page.hasNextPage()) {
                return result;
            }
            traceInfo = nextTraceInfo(traceInfo, page.nextTraceInfo(), pageNumber);
        }
        throw new BusinessException(ErrorCode.OPEN_BANKING_PAGINATION_ERROR);
    }

    private List<Transaction> fetchTransactions(String accessToken, Account account, SummaryPeriod period) {
        List<Transaction> result = new ArrayList<>();
        String traceInfo = null;
        for (int pageNumber = 0; pageNumber < MAX_PAGES; pageNumber++) {
            TransactionPage page = client.transactions(
                    accessToken, account.fintechUseNumber(), period.fromDate(), period.toDate(), traceInfo);
            result.addAll(page.transactions());
            if (!page.hasNextPage()) {
                return result;
            }
            traceInfo = nextTraceInfo(traceInfo, page.nextTraceInfo(), pageNumber);
        }
        throw new BusinessException(ErrorCode.OPEN_BANKING_PAGINATION_ERROR);
    }

    private BigDecimal fetchLoanRepaymentTotal(String accessToken, String userSeqNo, Loan loan, SummaryPeriod period) {
        BigDecimal result = BigDecimal.ZERO;
        String traceInfo = null;
        for (int pageNumber = 0; pageNumber < MAX_PAGES; pageNumber++) {
            LoanBasicPage page =
                    client.loanBasic(accessToken, userSeqNo, loan, period.fromDate(), period.toDate(), traceInfo);
            result = result.add(page.transactions().stream()
                    .filter(transaction -> "02".equals(transaction.type()))
                    .filter(transaction -> transaction.amount() != null)
                    .map(transaction -> transaction.amount().abs())
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            if (!page.hasNextPage()) {
                return result;
            }
            traceInfo = nextTraceInfo(traceInfo, page.nextTraceInfo(), pageNumber);
        }
        throw new BusinessException(ErrorCode.OPEN_BANKING_PAGINATION_ERROR);
    }

    private BigDecimal averageRepayment(LoanFetchResult loanResult, int repaymentSucceeded, BigDecimal repaymentTotal) {
        if (loanResult.loans().isEmpty()) {
            return loanResult.complete() ? BigDecimal.ZERO : null;
        }
        return repaymentSucceeded == 0 ? null : divide(repaymentTotal, SUMMARY_MONTHS);
    }

    private FinancialSummaryStatus status(
            List<OpenBankingDataWarning> warnings, int requestedSources, int succeededSources) {
        if (warnings.isEmpty()) {
            return FinancialSummaryStatus.COMPLETE;
        }
        return requestedSources > 0 && succeededSources == 0
                ? FinancialSummaryStatus.UNAVAILABLE
                : FinancialSummaryStatus.PARTIAL;
    }

    private void rethrowUnlessRecoverable(BusinessException exception) {
        if (exception.errorCode() != ErrorCode.OPEN_BANKING_PROVIDER_ERROR
                && exception.errorCode() != ErrorCode.OPEN_BANKING_PAGINATION_ERROR) {
            throw exception;
        }
    }

    private OpenBankingDataWarning warning(Source source, String reference, String code, String message) {
        return new OpenBankingDataWarning(source, reference, code, message);
    }

    private String nextTraceInfo(String currentTraceInfo, String nextTraceInfo, int pageNumber) {
        if (isBlank(nextTraceInfo) || nextTraceInfo.equals(currentTraceInfo) || pageNumber == MAX_PAGES - 1) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_PAGINATION_ERROR);
        }
        return nextTraceInfo;
    }

    private String loanKey(Loan loan) {
        String accountIdentifier = isBlank(loan.accountNumber()) ? loan.accountNumberMasked() : loan.accountNumber();
        return loan.bankCode()
                + '|'
                + accountIdentifier
                + '|'
                + loan.accountSequence()
                + '|'
                + loan.productName()
                + '|'
                + loan.accountType();
    }

    private boolean isSalary(Transaction transaction) {
        return transaction.date() != null
                && transaction.amount() != null
                && "입금".equals(transaction.direction())
                && "급여".equals(transaction.type());
    }

    private SummaryPeriod summaryPeriod() {
        LocalDate toDate = LocalDate.now(clock).withDayOfMonth(1).minusDays(1);
        LocalDate fromDate = toDate.withDayOfMonth(1).minusMonths(SUMMARY_MONTHS - 1L);
        return new SummaryPeriod(fromDate, toDate);
    }

    private BigDecimal divide(BigDecimal value, int divisor) {
        return value.divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP);
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 저축은행 계좌는 실제 이름이 savingsBankName 에 온다. 잔액 응답을 먼저 보고, 없으면 계좌 목록 값. */
    private String bankNameOf(Balance balance, Account account) {
        if (!isBlank(balance.savingsBankName())) {
            return balance.savingsBankName();
        }
        if (!isBlank(balance.bankName())) {
            return balance.bankName();
        }
        return !isBlank(account.savingsBankName()) ? account.savingsBankName() : account.bankName();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SummaryPeriod(LocalDate fromDate, LocalDate toDate) {}

    private record LoanFetchResult(List<Loan> loans, int requestedBankCodes, int succeededBankCodes) {
        private boolean complete() {
            return requestedBankCodes == succeededBankCodes;
        }
    }
}
