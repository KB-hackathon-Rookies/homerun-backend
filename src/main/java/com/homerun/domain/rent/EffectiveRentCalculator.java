package com.homerun.domain.rent;

import org.springframework.stereotype.Service;

/**
 * 지원금과 세액공제를 반영한 실질 월 주거비를 계산한다(POL-04-09).
 *
 * <p>전세와 월세를 비교할 때 명목 월세로만 보면 월세가 부당하게 불리해 보인다. 지원금과
 * 세액공제를 반영해야 같은 기준의 비교가 된다.
 */
@Service
public class EffectiveRentCalculator {

    private final RentSupportResolver supportResolver;
    private final RentTaxCreditCalculator taxCreditCalculator;

    public EffectiveRentCalculator(RentSupportResolver supportResolver, RentTaxCreditCalculator taxCreditCalculator) {
        this.supportResolver = supportResolver;
        this.taxCreditCalculator = taxCreditCalculator;
    }

    public EffectiveRentResult calculate(EffectiveRentRequest request) {
        // 지원금부터 확정한다. 배타 관계를 풀지 않으면 받지 못할 금액이 섞인다.
        RentSupportCombination support = supportResolver.recommend(request.eligibleSupports());

        TaxCreditResult taxCredit = taxCreditCalculator.calculate(new TaxCreditRequest(
                request.monthlyRent(),
                request.months(),
                request.annualSalary(),
                support.amountWithinYear(),
                request.homeless(),
                request.residentRegistered()));

        long monthlySupport = support.monthlyAmount();
        long monthlyTaxCredit = taxCredit.refund() / 12;
        long effectiveRent = Math.max(0, request.monthlyRent() - monthlySupport - monthlyTaxCredit);

        return new EffectiveRentResult(
                request.monthlyRent(),
                request.managementFee(),
                monthlySupport,
                monthlyTaxCredit,
                effectiveRent,
                effectiveRent + request.managementFee(),
                support,
                taxCredit);
    }
}
