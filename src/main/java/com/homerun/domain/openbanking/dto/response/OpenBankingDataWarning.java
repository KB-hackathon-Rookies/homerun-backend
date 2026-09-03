package com.homerun.domain.openbanking.dto.response;

public record OpenBankingDataWarning(Source source, String reference, String code, String message) {

    public enum Source {
        ACCOUNT_BALANCE,
        ACCOUNT_TRANSACTIONS,
        LOAN_LIST,
        LOAN_REPAYMENT
    }
}
