package com.homerun.rent;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 상품 하나의 대출 견적.
 *
 * @param product 상품
 * @param depositLoan 보증금분 대출액(원). 주거안정은 0 이다
 * @param monthlyRentLoan 월세분 월 대출액(원)
 * @param termMonths 대출 기간(개월)
 * @param monthlyInterest 첫 달 이자(원). 만기일시상환이라 월 부담은 이자뿐이다
 * @param totalInterest 기간 전체 이자(원)
 * @param provisional 기준 수치가 확정 전이라 바뀔 수 있는가
 * @param notes 사용자에게 함께 보여줄 안내
 */
@Schema(description = "월세대출 견적")
public record RentLoanQuote(
        RentLoanProduct product,
        long depositLoan,
        long monthlyRentLoan,
        int termMonths,
        long monthlyInterest,
        long totalInterest,
        boolean provisional,
        List<String> notes) {

    public RentLoanQuote {
        notes = List.copyOf(notes);
    }
}
