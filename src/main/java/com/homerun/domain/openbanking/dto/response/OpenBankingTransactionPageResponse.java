package com.homerun.domain.openbanking.dto.response;

import com.homerun.global.external.openbanking.OpenBankingResponses.Transaction;
import com.homerun.global.external.openbanking.OpenBankingResponses.TransactionPage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record OpenBankingTransactionPageResponse(
        String bankName,
        String savingsBankName,
        String fintechUseNumber,
        BigDecimal currentBalance,
        boolean hasNextPage,
        String nextTraceInfo,
        List<TransactionResponse> transactions,
        Instant fetchedAt) {

    public static OpenBankingTransactionPageResponse from(TransactionPage page) {
        return new OpenBankingTransactionPageResponse(
                page.bankName(),
                page.savingsBankName(),
                page.fintechUseNumber(),
                page.currentBalance(),
                page.hasNextPage(),
                page.nextTraceInfo(),
                page.transactions().stream().map(TransactionResponse::from).toList(),
                page.fetchedAt());
    }

    public record TransactionResponse(
            LocalDate date,
            LocalTime time,
            String direction,
            String type,
            String description,
            BigDecimal amount,
            BigDecimal balanceAfterTransaction,
            String branchName) {

        static TransactionResponse from(Transaction transaction) {
            return new TransactionResponse(
                    transaction.date(),
                    transaction.time(),
                    transaction.direction(),
                    transaction.type(),
                    transaction.description(),
                    transaction.amount(),
                    transaction.balanceAfterTransaction(),
                    transaction.branchName());
        }
    }
}
