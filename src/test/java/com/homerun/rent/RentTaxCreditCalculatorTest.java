package com.homerun.rent;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RentTaxCreditCalculatorTest {

    private final RentTaxCreditCalculator calculator;

    RentTaxCreditCalculatorTest(@Autowired RentTaxCreditCalculator calculator) {
        this.calculator = calculator;
    }

    private static TaxCreditRequest request(long monthlyRent, long salary, long support) {
        return new TaxCreditRequest(monthlyRent, 12, salary, support, true, true);
    }

    @Test
    @DisplayName("총급여 5,500만 이하면 17% 를 적용한다")
    void should_apply_higher_rate_under_55m() {
        // 월세 50만 × 12 = 600만, 지원금 없음 → 600만 × 17% = 102만
        TaxCreditResult result = calculator.calculate(request(500_000, 42_000_000, 0));

        assertThat(result.eligible()).isTrue();
        assertThat(result.creditBase()).isEqualTo(6_000_000L);
        assertThat(result.refund()).isEqualTo(1_020_000L);
    }

    @Test
    @DisplayName("총급여 5,500만 초과 7,000만 이하면 15% 를 적용한다")
    void should_apply_lower_rate_over_55m() {
        TaxCreditResult result = calculator.calculate(request(500_000, 60_000_000, 0));

        assertThat(result.refund()).isEqualTo(900_000L);
    }

    @Test
    @DisplayName("지원금을 먼저 빼고 남은 본인 부담분에만 공제율을 적용한다")
    void should_deduct_support_before_applying_rate() {
        // 월세 600만 − 지원금 240만 = 본인부담 360만 → × 17% = 61.2만
        TaxCreditResult result = calculator.calculate(request(500_000, 42_000_000, 2_400_000));

        assertThat(result.supportDeducted()).isEqualTo(2_400_000L);
        assertThat(result.creditBase()).isEqualTo(3_600_000L);
        assertThat(result.refund()).isEqualTo(612_000L);
    }

    @Test
    @DisplayName("각각 계산해 더하는 방식보다 환급액이 작아야 한다")
    void should_be_smaller_than_naive_sum() {
        // 순서를 뒤집어 월세 전액에 공제율을 적용하면 102만이 나온다. 그건 과대계산이다
        long wrong = 1_020_000L;
        TaxCreditResult result = calculator.calculate(request(500_000, 42_000_000, 2_400_000));

        assertThat(result.refund()).isLessThan(wrong);
    }

    @Test
    @DisplayName("지원금이 월세보다 크면 공제 대상이 0 이 된다")
    void should_floor_credit_base_at_zero() {
        TaxCreditResult result = calculator.calculate(request(200_000, 42_000_000, 5_000_000));

        assertThat(result.creditBase()).isZero();
        assertThat(result.refund()).isZero();
    }

    @Test
    @DisplayName("연 750만원 한도를 넘는 월세는 잘라낸다")
    void should_cap_credit_base_at_annual_limit() {
        // 월세 80만 × 12 = 960만이지만 공제 대상은 750만까지다
        TaxCreditResult result = calculator.calculate(request(800_000, 42_000_000, 0));

        assertThat(result.annualRent()).isEqualTo(9_600_000L);
        assertThat(result.creditBase()).isEqualTo(7_500_000L);
        assertThat(result.refund()).isEqualTo(1_275_000L);
    }

    @Test
    @DisplayName("최대 환급액은 127.5만원을 넘지 않는다")
    void should_not_exceed_max_refund() {
        TaxCreditResult result = calculator.calculate(request(2_000_000, 30_000_000, 0));

        assertThat(result.refund()).isEqualTo(1_275_000L);
    }

    @Test
    @DisplayName("총급여 7,000만원을 넘으면 대상이 아니다")
    void should_reject_over_salary_cap() {
        TaxCreditResult result = calculator.calculate(request(500_000, 70_000_001, 0));

        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).contains("7,000만원");
    }

    @Test
    @DisplayName("전입신고를 하지 않으면 대상이 아니다")
    void should_require_resident_registration() {
        TaxCreditResult result = calculator.calculate(new TaxCreditRequest(500_000, 12, 42_000_000, 0, true, false));

        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).contains("전입신고");
    }

    @Test
    @DisplayName("무주택이 아니면 대상이 아니다")
    void should_require_homeless() {
        TaxCreditResult result = calculator.calculate(new TaxCreditRequest(500_000, 12, 42_000_000, 0, false, true));

        assertThat(result.eligible()).isFalse();
    }

    @Test
    @DisplayName("연중 입주면 실제 거주 개월 수만 계산한다")
    void should_count_actual_months() {
        TaxCreditResult result = calculator.calculate(new TaxCreditRequest(500_000, 5, 42_000_000, 0, true, true));

        assertThat(result.annualRent()).isEqualTo(2_500_000L);
        assertThat(result.refund()).isEqualTo(425_000L);
    }
}
