package com.homerun.domain.settlement.service;

import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.settlement.dto.response.TaxDeductionResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * 주택임차차입금 원리금상환액 소득공제(BR-29, FR-H8-01). 판정이 아니라 산술 계산이라 저장하지
 * 않고 공제율·상한·세율은 전부 config_effective 에서 읽는다.
 *
 * <p>공제액 = min(min(연상환액, 1,000만) × 40%, 400만). 예상 환급 = 공제액 × 간이세율(16.5%).
 * 만기일시상환은 이자만 대상이라 연상환액 = 대출금 × 금리다.
 */
@Service
public class HousingTaxDeductionCalculator {

    private static final String DEDUCTION_RATE = "FCT-239"; // 공제율 40%
    private static final String REPAYMENT_CAP = "FCT-240"; // 상환액 상한 1,000만
    private static final String DEDUCTION_LIMIT = "FCT-053"; // 소득공제 한도 400만
    private static final String SIMPLIFIED_RATE = "FCT-241"; // 간이세율 16.5%

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final FactRegistry facts;

    public HousingTaxDeductionCalculator(FactRegistry facts) {
        this.facts = facts;
    }

    /**
     * @param annualRepaymentOverride 만기일시가 아닐 때의 연 상환액. 만기일시면 무시하고 이자로 계산
     */
    public TaxDeductionResponse calculate(
            long loanAmount, BigDecimal annualRatePercent, boolean maturityLumpSum, Long annualRepaymentOverride) {
        Long annualRepayment = annualRepayment(loanAmount, annualRatePercent, maturityLumpSum, annualRepaymentOverride);
        if (annualRepayment == null) {
            // 원리금균등인데 상환액을 모르면 공제를 지어내지 않는다.
            return new TaxDeductionResponse(null, null, null, false);
        }

        long cap = facts.won(REPAYMENT_CAP);
        long limit = facts.won(DEDUCTION_LIMIT);

        long deductionBase = Math.min(annualRepayment, cap);
        long deduction = Math.min(won(BigDecimal.valueOf(deductionBase).multiply(ratioOfFact(DEDUCTION_RATE))), limit);
        long refund = won(BigDecimal.valueOf(deduction).multiply(ratioOfFact(SIMPLIFIED_RATE)));
        boolean simplified = facts.require(SIMPLIFIED_RATE).provisional();
        return new TaxDeductionResponse(annualRepayment, deduction, refund, simplified);
    }

    /** 만기일시상환이면 연 이자, 아니면 입력받은 연 상환액(없으면 null). */
    private Long annualRepayment(long loanAmount, BigDecimal ratePercent, boolean maturityLumpSum, Long override) {
        if (maturityLumpSum) {
            return won(BigDecimal.valueOf(loanAmount).multiply(ratioOf(ratePercent)));
        }
        return override;
    }

    private BigDecimal ratioOfFact(String factCode) {
        return ratioOf(facts.require(factCode).requireNumber());
    }

    private BigDecimal ratioOf(BigDecimal percent) {
        return percent.divide(HUNDRED, 10, RoundingMode.HALF_UP);
    }

    private long won(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
