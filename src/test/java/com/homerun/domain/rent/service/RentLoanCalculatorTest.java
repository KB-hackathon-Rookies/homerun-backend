package com.homerun.domain.rent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.rent.dto.request.RentLoanRequest;
import com.homerun.domain.rent.dto.response.RentLoanQuote;
import com.homerun.domain.rent.type.RentLoanProduct;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RentLoanCalculatorTest {

    private final RentLoanCalculator calculator;

    RentLoanCalculatorTest(@Autowired RentLoanCalculator calculator) {
        this.calculator = calculator;
    }

    @Test
    @DisplayName("월세가 무이자 구간 안이면 월세분 이자가 없다")
    void should_charge_nothing_within_free_band() {
        // 월세 20만원까지는 연 0%
        RentLoanQuote quote = calculator.quoteDepositBacked(new RentLoanRequest(30_000_000, 200_000, 24, false));

        long depositOnly = calculator
                .quoteDepositBacked(new RentLoanRequest(30_000_000, 0, 24, false))
                .totalInterest();
        assertThat(quote.totalInterest()).isEqualTo(depositOnly);
        assertThat(quote.notes()).anySatisfy(note -> assertThat(note).contains("무이자 구간"));
    }

    @Test
    @DisplayName("무이자 구간을 넘는 월세분에만 이자가 붙는다")
    void should_charge_only_excess_over_free_band() {
        // 월세 50만 → 초과분 30만에만 연 1.0%
        RentLoanQuote quote = calculator.quoteDepositBacked(new RentLoanRequest(0, 500_000, 24, false));

        // 매월 30만 인출, 만기일시상환 → 30만 × 1% / 12 × (24×25/2)
        assertThat(quote.totalInterest()).isEqualTo(75_000L);
    }

    @Test
    @DisplayName("월세분 이자를 마지막 달 잔액으로 계산하면 과대계산이다")
    void should_not_use_final_balance_for_whole_term() {
        RentLoanQuote quote = calculator.quoteDepositBacked(new RentLoanRequest(0, 500_000, 24, false));

        // 잔액 720만(30만×24)에 24개월 이자를 매기면 144,000원이 된다. 그건 두 배 가까이 크다
        assertThat(quote.totalInterest()).isLessThan(144_000L);
    }

    @Test
    @DisplayName("보증금은 4,500만원까지만 대출 대상이다")
    void should_cap_deposit_loan() {
        RentLoanQuote quote = calculator.quoteDepositBacked(new RentLoanRequest(60_000_000, 300_000, 24, false));

        assertThat(quote.depositLoan()).isEqualTo(45_000_000L);
        assertThat(quote.notes()).anySatisfy(note -> assertThat(note).contains("45,000,000원까지만"));
    }

    @Test
    @DisplayName("월세분 기간은 24개월을 넘지 않는다")
    void should_cap_term_at_24_months() {
        RentLoanQuote quote = calculator.quoteDepositBacked(new RentLoanRequest(0, 500_000, 36, false));

        assertThat(quote.termMonths()).isEqualTo(24);
    }

    @Test
    @DisplayName("주거안정은 보증금을 빌려주지 않는다")
    void should_not_lend_deposit_in_stability_product() {
        RentLoanQuote quote = calculator.quoteHousingStability(new RentLoanRequest(30_000_000, 500_000, 24, true));

        assertThat(quote.depositLoan()).isZero();
        assertThat(quote.notes()).anySatisfy(note -> assertThat(note).contains("보증금은 이 상품으로"));
    }

    @Test
    @DisplayName("주거안정 우대형이 일반형보다 이자가 적다")
    void should_charge_less_for_preferential() {
        long preferential = calculator
                .quoteHousingStability(new RentLoanRequest(0, 500_000, 24, true))
                .totalInterest();
        long standard = calculator
                .quoteHousingStability(new RentLoanRequest(0, 500_000, 24, false))
                .totalInterest();

        assertThat(preferential).isLessThan(standard);
    }

    @Test
    @DisplayName("주거안정은 월 60만원까지만 대출 대상이다")
    void should_cap_stability_monthly_amount() {
        RentLoanQuote quote = calculator.quoteHousingStability(new RentLoanRequest(0, 800_000, 24, true));

        assertThat(quote.monthlyRentLoan()).isEqualTo(600_000L);
    }

    @Test
    @DisplayName("주거안정 금리는 확정 전이라 변경 가능으로 표시한다")
    void should_flag_stability_rate_as_provisional() {
        // FCT-028 계열은 확정도 REVIEW 다
        assertThat(calculator
                        .quoteHousingStability(new RentLoanRequest(0, 500_000, 24, true))
                        .provisional())
                .isTrue();
    }

    @Test
    @DisplayName("요청 자금을 채우는 상품을 앞에 두고 그 안에서 총 이자로 정렬한다")
    void should_sort_by_coverage_then_interest() {
        List<RentLoanQuote> quotes = calculator.compare(new RentLoanRequest(30_000_000, 500_000, 24, true));

        assertThat(quotes).hasSize(2);
        // 보증금을 못 빌려주는 상품은 이자가 싸도 뒤로 간다
        assertThat(quotes.get(0).coversRequest()).isTrue();
    }

    @Test
    @DisplayName("보증금이 큰 계약은 보증금분 이자가 총액을 좌우한다")
    void should_reflect_deposit_interest_in_total() {
        RentLoanQuote quote = calculator.quoteDepositBacked(new RentLoanRequest(45_000_000, 200_000, 24, false));

        // 4,500만 × 1.3% / 12 = 월 48,750원
        assertThat(quote.monthlyInterest()).isEqualTo(48_750L);
        assertThat(quote.totalInterest()).isEqualTo(48_750L * 24);
    }

    @Test
    @DisplayName("보증금이 필요한데 못 빌려주는 상품은 자금 공백을 알려준다")
    void should_report_funding_gap() {
        RentLoanQuote quote = calculator.quoteHousingStability(new RentLoanRequest(30_000_000, 500_000, 24, true));

        assertThat(quote.coversRequest()).isFalse();
        assertThat(quote.fundingGap()).isGreaterThanOrEqualTo(30_000_000L);
        assertThat(quote.notes()).anySatisfy(note -> assertThat(note).contains("채울 수 없다"));
    }

    @Test
    @DisplayName("이자가 싸도 요청 자금을 못 채우면 앞에 오지 않는다")
    void should_not_rank_incomplete_product_first() {
        // 주거안정은 이자가 더 싸지만 보증금 3천만원을 빌려주지 않는다
        List<RentLoanQuote> quotes = calculator.compare(new RentLoanRequest(30_000_000, 200_000, 24, true));

        assertThat(quotes.get(0).coversRequest()).isTrue();
        assertThat(quotes.get(0).product()).isEqualTo(RentLoanProduct.DEPOSIT_BACKED);
    }

    @Test
    @DisplayName("보증금이 없으면 두 상품 다 요청을 채운다")
    void should_cover_when_no_deposit_needed() {
        List<RentLoanQuote> quotes = calculator.compare(new RentLoanRequest(0, 500_000, 24, true));

        assertThat(quotes).allSatisfy(q -> assertThat(q.coversRequest()).isTrue());
        assertThat(quotes.get(0).totalInterest())
                .isLessThanOrEqualTo(quotes.get(1).totalInterest());
    }
}
