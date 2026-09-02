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
}
