package com.homerun.domain.rent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 월세 세액공제 계산 결과.
 *
 * @param eligible 공제 대상인가
 * @param annualRent 연간 월세 총액(원)
 * @param supportDeducted 공제 대상에서 빠지는 지원금(원)
 * @param creditBase 공제 대상 금액(원). 한도 적용 후
 * @param rate 적용 공제율
 * @param refund 예상 환급액(원)
 * @param reason 대상이 아닐 때의 사유
 */
@Schema(description = "월세 세액공제 계산 결과")
public record TaxCreditResult(
        boolean eligible,
        long annualRent,
        long supportDeducted,
        long creditBase,
        java.math.BigDecimal rate,
        long refund,
        String reason) {

    public static TaxCreditResult notEligible(long annualRent, String reason) {
        return new TaxCreditResult(false, annualRent, 0, 0, java.math.BigDecimal.ZERO, 0, reason);
    }
}
