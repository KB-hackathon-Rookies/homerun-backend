package com.homerun.domain.openbanking.dto.response;

import com.homerun.global.external.openbanking.OpenBankingResponses.Account;

public record OpenBankingAccountResponse(
        String alias,
        String bankCode,
        String bankName,
        String savingsBankName,
        String fintechUseNumber,
        String accountNumberMasked,
        String accountHolderName,
        String accountType) {

    public static OpenBankingAccountResponse from(Account account) {
        return new OpenBankingAccountResponse(
                account.accountAlias(),
                account.bankCode(),
                account.bankName(),
                account.savingsBankName(),
                account.fintechUseNumber(),
                account.accountNumberMasked(),
                account.accountHolderName(),
                account.accountType());
    }
}
