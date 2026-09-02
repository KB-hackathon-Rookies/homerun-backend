package com.homerun.rent;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 실질 월 주거비.
 *
 * @param nominalRent 명목 월세(원)
 * @param managementFee 월 관리비(원)
 * @param monthlySupport 월 지원금(원)
 * @param monthlyTaxCredit 세액공제 환급액을 12로 나눈 월 환산액(원)
 * @param effectiveRent 실질 월세(원). 관리비는 빼고 본 값
 * @param effectiveHousingCost 실질 월 주거비(원). 관리비까지 포함한 실제 지출
 * @param support 적용된 지원금 조합
 * @param taxCredit 세액공제 계산 결과
 */
@Schema(description = "지원금과 세액공제를 반영한 실질 월 주거비")
public record EffectiveRentResult(
        long nominalRent,
        long managementFee,
        long monthlySupport,
        long monthlyTaxCredit,
        long effectiveRent,
        long effectiveHousingCost,
        RentSupportCombination support,
        TaxCreditResult taxCredit) {}
