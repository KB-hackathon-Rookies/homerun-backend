package com.homerun.domain.openbanking.dto.response;

import com.homerun.global.external.openbanking.OpenBankingResponses.Balance;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record OpenBankingBalanceResponse(
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
        Instant fetchedAt) {

    public static OpenBankingBalanceResponse from(Balance balance) {
        return new OpenBankingBalanceResponse(
                balance.bankName(),
                balance.savingsBankName(),
                balance.fintechUseNumber(),
                balance.balanceAmount(),
                balance.availableAmount(),
                balance.accountType(),
                balance.productName(),
                balance.accountIssueDate(),
                balance.maturityDate(),
                balance.lastTransactionDate(),
                balance.fetchedAt());
    }
}
