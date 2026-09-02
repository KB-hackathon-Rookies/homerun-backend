package com.homerun.rent;

import com.homerun.fact.FactRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * 월세 세액공제를 계산한다(POL-04-05, POL-04-06).
 *
 * <p>계산 순서가 이 클래스의 핵심이다. 지원금을 먼저 빼고 남은 본인 부담분에만 공제율을
 * 적용해야 한다. 순서를 뒤집어 각각 계산한 뒤 더하면, 나라가 대신 내준 월세까지 공제받는
 * 것으로 잡혀 환급액이 과대계산된다(FCT-052).
 */
@Service
public class RentTaxCreditCalculator {

    /** 공제 대상 월세의 연 한도. */
    private static final String ANNUAL_CAP = "FCT-046";
    /** 총급여 5,500만원 이하 구간의 공제율 경계. */
    private static final long HIGHER_RATE_SALARY_CAP = 55_000_000L;
    /** 공제를 받을 수 있는 총급여 상한. */
    private static final long ELIGIBLE_SALARY_CAP = 70_000_000L;

    private static final BigDecimal HIGHER_RATE = new BigDecimal("0.17");
    private static final BigDecimal LOWER_RATE = new BigDecimal("0.15");

    private final FactRegistry facts;

    public RentTaxCreditCalculator(FactRegistry facts) {
        this.facts = facts;
    }

    public TaxCreditResult calculate(TaxCreditRequest request) {
        long annualRent = request.annualRent();

        if (!request.homeless()) {
            return TaxCreditResult.notEligible(annualRent, "무주택 세대주 또는 세대원이어야 공제 대상이다");
        }
        if (!request.residentRegistered()) {
            return TaxCreditResult.notEligible(annualRent, "전입신고를 마쳐야 공제 대상이다");
        }
        if (request.annualSalary() > ELIGIBLE_SALARY_CAP) {
            return TaxCreditResult.notEligible(annualRent, "총급여 7,000만원을 넘으면 공제 대상이 아니다");
        }

        // POL-04-06. 지원금을 먼저 뺀다. 이 두 줄의 순서를 바꾸면 안 된다.
        long selfPaid = Math.max(0, annualRent - request.supportWithinYear());
        long creditBase = Math.min(selfPaid, facts.won(ANNUAL_CAP));

        BigDecimal rate = request.annualSalary() <= HIGHER_RATE_SALARY_CAP ? HIGHER_RATE : LOWER_RATE;
        long refund = BigDecimal.valueOf(creditBase)
                .multiply(rate)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();

        return new TaxCreditResult(
                true, annualRent, Math.min(request.supportWithinYear(), annualRent), creditBase, rate, refund, null);
    }
}
