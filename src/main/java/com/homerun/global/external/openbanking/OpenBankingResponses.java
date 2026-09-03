package com.homerun.global.external.openbanking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class OpenBankingResponses {

    private OpenBankingResponses() {}

    public record Token(
            String accessToken,
            String refreshToken,
            String tokenType,
            String scope,
            String userSeqNo,
            long expiresInSeconds,
            Long refreshTokenExpiresInSeconds) {}

    public record Account(
            String accountAlias,
            String bankCode,
            String bankName,
            String savingsBankName,
            String fintechUseNumber,
            String accountNumberMasked,
            String accountHolderName,
            String accountType) {}

    public record UserInfo(String userSeqNo, String userName, List<Account> accounts) {}

    public record Balance(
            String bankName,
            String savingsBankName,
            String fintechUseNumber,
            BigDecimal balanceAmount,
            BigDecimal availableAmount,
            String accountType,
            String productName,
            LocalDate accountIssueDate,
            LocalDate maturityDate,
            LocalDate lastTransactionDate,
            Instant fetchedAt) {}

    public record Transaction(
            LocalDate date,
            LocalTime time,
            String direction,
            String type,
            String description,
            BigDecimal amount,
            BigDecimal balanceAfterTransaction,
            String branchName) {}

    public record TransactionPage(
            String bankName,
            String savingsBankName,
            String fintechUseNumber,
            BigDecimal currentBalance,
            boolean hasNextPage,
            String nextTraceInfo,
            List<Transaction> transactions,
            Instant fetchedAt) {}

    public record Loan(
            String bankCode,
            String bankName,
            String accountNumber,
            String accountSequence,
            String accountNumberMasked,
            String productName,
            String accountType,
            String accountStatus) {}

    public record LoanPage(boolean hasNextPage, String nextTraceInfo, List<Loan> loans, Instant fetchedAt) {}

    public record LoanTransaction(LocalDate date, LocalTime time, String type, BigDecimal amount) {}

    public record LoanBasicPage(
            String repaymentDate,
            String repaymentMethod,
            String repaymentOrganizationCode,
            LocalDate nextRepaymentDate,
            boolean hasNextPage,
            String nextTraceInfo,
            List<LoanTransaction> transactions,
            Instant fetchedAt) {}
}
