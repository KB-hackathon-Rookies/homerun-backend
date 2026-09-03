package com.homerun.domain.openbanking.dto.response;

import com.homerun.global.external.openbanking.OpenBankingResponses.Loan;

public record OpenBankingLoanResponse(
        String bankCode,
        String bankName,
        String accountNumberMasked,
        String productName,
        String accountType,
        String accountTypeName,
        String accountStatus,
        boolean repaymentDetailsAvailable) {

    public static OpenBankingLoanResponse from(Loan loan) {
        return new OpenBankingLoanResponse(
                loan.bankCode(),
                loan.bankName(),
                loan.accountNumberMasked(),
                loan.productName(),
                loan.accountType(),
                accountTypeName(loan.accountType()),
                loan.accountStatus(),
                loan.accountNumber() != null && !loan.accountNumber().isBlank());
    }

    private static String accountTypeName(String type) {
        return switch (type) {
            case "3100" -> "신용대출";
            case "3150" -> "학자금대출";
            case "3170" -> "전세자금대출";
            case "3200" -> "예적금담보대출";
            case "3210" -> "유가증권담보대출";
            case "3220" -> "주택담보대출";
            case "3230" -> "기타부동산담보대출";
            case "3240" -> "지급보증담보대출";
            case "3245" -> "보금자리론";
            case "3250" -> "학자금지급보증담보대출";
            case "3260" -> "주택연금대출";
            case "3270" -> "보증서담보전세자금대출";
            case "3271" -> "전세보증금담보대출";
            case "3290" -> "기타담보대출";
            case "3400" -> "보험계약대출";
            case "3500" -> "신차할부금융";
            case "3510" -> "중고차할부금융";
            case "3590" -> "기타할부금융";
            case "3700" -> "금융리스";
            case "3710" -> "운용리스";
            default -> "기타대출";
        };
    }
}
