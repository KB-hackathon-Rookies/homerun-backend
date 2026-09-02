package com.homerun.rent;

import com.homerun.fact.Fact;
import com.homerun.fact.FactRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 월세대출 두 상품의 견적을 내고 비교한다(POL-04-01, POL-04-02, POL-04-03).
 *
 * <p>둘 다 만기일시상환이라 월 부담은 이자뿐이다. 표면 금리만 비교하면 안 되는 이유는
 * 대출 구조가 다르기 때문이다. 보증부월세는 보증금과 월세를 둘 다 빌리고, 주거안정은
 * 월세자금만 빌린다.
 */
@Service
public class RentLoanCalculator {

    private static final String DEPOSIT_CAP = "FCT-142";
    private static final String DEPOSIT_RATE = "FCT-143";
    private static final String RENT_CAP = "FCT-144";
    private static final String RENT_MAX_MONTHS = "FCT-145";
    private static final String RENT_FREE_BAND = "FCT-146";
    private static final String RENT_EXCESS_RATE = "FCT-147";
    private static final String STABILITY_CAP = "FCT-148";
    private static final String STABILITY_PREFERENTIAL_RATE = "FCT-149";
    private static final String STABILITY_STANDARD_RATE = "FCT-150";

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    private final FactRegistry facts;

    public RentLoanCalculator(FactRegistry facts) {
        this.facts = facts;
    }

    /** 두 상품을 모두 계산해 총 이자가 적은 순으로 준다(POL-04-03). */
    public List<RentLoanQuote> compare(RentLoanRequest request) {
        List<RentLoanQuote> quotes = new ArrayList<>();
        quotes.add(quoteDepositBacked(request));
        quotes.add(quoteHousingStability(request));
        quotes.sort(Comparator.comparingLong(RentLoanQuote::totalInterest));
        return List.copyOf(quotes);
    }

    /**
     * 청년전용 보증부월세대출(POL-04-01).
     *
     * <p>월세분은 월 20만원까지 무이자다. 이 구간을 빼먹으면 월 부담이 과대하게 나온다.
     */
    public RentLoanQuote quoteDepositBacked(RentLoanRequest request) {
        List<String> notes = new ArrayList<>();

        long depositLoan = Math.min(request.deposit(), facts.won(DEPOSIT_CAP));
        if (request.deposit() > depositLoan) {
            notes.add("보증금 중 %,d원까지만 대출 대상이다".formatted(depositLoan));
        }

        long rentLoan = Math.min(request.monthlyRent(), facts.won(RENT_CAP));
        int maxMonths = (int) facts.require(RENT_MAX_MONTHS).requireNumber().longValueExact();
        int term = Math.min(request.termMonths(), maxMonths);
        if (request.termMonths() > maxMonths) {
            notes.add("월세분은 최장 %d개월까지다".formatted(maxMonths));
        }

        long freeBand = facts.won(RENT_FREE_BAND);
        long chargeable = Math.max(0, rentLoan - freeBand);
        if (chargeable == 0 && rentLoan > 0) {
            notes.add("월세분이 무이자 구간(월 %,d원) 안이라 월세분 이자가 없다".formatted(freeBand));
        }

        BigDecimal depositRate = rate(DEPOSIT_RATE);
        BigDecimal excessRate = rate(RENT_EXCESS_RATE);

        long depositMonthlyInterest = monthlyInterest(depositLoan, depositRate);

        // 월세분은 매월 인출돼 잔액이 늘어난다. 마지막 달 잔액으로 전기간을 계산하면 과대계산이다.
        long rentTotalInterest = drawdownInterest(chargeable, excessRate, term);
        long depositTotalInterest = depositMonthlyInterest * term;

        notes.add("만기일시상환이라 매달 이자만 낸다. 원금은 만기에 갚는다");

        return new RentLoanQuote(
                RentLoanProduct.DEPOSIT_BACKED,
                depositLoan,
                rentLoan,
                term,
                depositMonthlyInterest + monthlyInterest(chargeable, excessRate),
                depositTotalInterest + rentTotalInterest,
                facts.require(RENT_FREE_BAND).provisional(),
                notes);
    }

    /** 주거안정 월세대출(POL-04-02). 월세자금만 빌린다. */
    public RentLoanQuote quoteHousingStability(RentLoanRequest request) {
        List<String> notes = new ArrayList<>();

        long rentLoan = Math.min(request.monthlyRent(), facts.won(STABILITY_CAP));
        if (request.monthlyRent() > rentLoan) {
            notes.add("월세 중 %,d원까지만 대출 대상이다".formatted(rentLoan));
        }

        String rateCode = request.preferential() ? STABILITY_PREFERENTIAL_RATE : STABILITY_STANDARD_RATE;
        Fact rateFact = facts.require(rateCode);
        BigDecimal rate = rateFact.requireNumber().divide(PERCENT, 10, RoundingMode.HALF_UP);

        notes.add(request.preferential() ? "우대형 금리를 적용했다" : "일반형 금리를 적용했다");
        notes.add("보증금은 이 상품으로 빌릴 수 없다");

        return new RentLoanQuote(
                RentLoanProduct.HOUSING_STABILITY,
                0,
                rentLoan,
                request.termMonths(),
                monthlyInterest(rentLoan, rate),
                drawdownInterest(rentLoan, rate, request.termMonths()),
                rateFact.provisional(),
                notes);
    }

    private BigDecimal rate(String factCode) {
        return facts.require(factCode).requireNumber().divide(PERCENT, 10, RoundingMode.HALF_UP);
    }

    /** 일시 인출한 원금의 한 달 이자. */
    private long monthlyInterest(long principal, BigDecimal annualRate) {
        return BigDecimal.valueOf(principal)
                .multiply(annualRate)
                .divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }

    /**
     * 매월 같은 금액을 인출할 때의 기간 전체 이자.
     *
     * <p>1회차 인출분은 n개월, 2회차는 n-1개월치 이자가 붙는다. 합이 n(n+1)/2 개월분이다.
     */
    private long drawdownInterest(long monthlyDraw, BigDecimal annualRate, int months) {
        if (monthlyDraw == 0 || months == 0) {
            return 0;
        }
        BigDecimal weightedMonths = BigDecimal.valueOf((long) months * (months + 1) / 2);
        return BigDecimal.valueOf(monthlyDraw)
                .multiply(annualRate)
                .divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP)
                .multiply(weightedMonths)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }
}
