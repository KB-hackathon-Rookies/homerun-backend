package com.homerun.rent;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EffectiveRentCalculatorTest {

    private final EffectiveRentCalculator calculator;

    EffectiveRentCalculatorTest(@Autowired EffectiveRentCalculator calculator) {
        this.calculator = calculator;
    }

    private static RentSupportOption option(RentSupportType type, long monthly, int months) {
        return new RentSupportOption(type, monthly, months, false);
    }

    @Test
    @DisplayName("지원금이 없으면 세액공제만큼만 명목 월세보다 싸다")
    void should_reduce_by_tax_credit_only() {
        // 월세 50만 · 총급여 4,200만 → 환급 102만 → 월 85,000원
        EffectiveRentResult result =
                calculator.calculate(new EffectiveRentRequest(500_000, 70_000, 12, 42_000_000, List.of(), true, true));

        assertThat(result.monthlySupport()).isZero();
        assertThat(result.monthlyTaxCredit()).isEqualTo(85_000L);
        assertThat(result.effectiveRent()).isEqualTo(415_000L);
    }

    @Test
    @DisplayName("관리비는 실질 월세에서 빼지 않고 주거비에만 더한다")
    void should_keep_management_fee_out_of_rent() {
        EffectiveRentResult result =
                calculator.calculate(new EffectiveRentRequest(500_000, 70_000, 12, 42_000_000, List.of(), true, true));

        assertThat(result.effectiveRent()).isEqualTo(415_000L);
        assertThat(result.effectiveHousingCost()).isEqualTo(485_000L);
    }

    @Test
    @DisplayName("지원금을 받으면 세액공제가 줄어 실질 월세가 단순 차감보다 덜 내려간다")
    void should_account_for_reduced_tax_credit_when_supported() {
        // 월 지원 20만. 단순히 빼면 415,000 − 200,000 = 215,000 이지만
        // 지원금만큼 공제 대상이 줄어 환급이 102만 → 61.2만으로 떨어진다
        EffectiveRentResult result = calculator.calculate(new EffectiveRentRequest(
                500_000,
                0,
                12,
                42_000_000,
                List.of(option(RentSupportType.YOUTH_RENT_NATIONAL, 200_000, 12)),
                true,
                true));

        assertThat(result.monthlySupport()).isEqualTo(200_000L);
        assertThat(result.taxCredit().refund()).isEqualTo(612_000L);
        assertThat(result.monthlyTaxCredit()).isEqualTo(51_000L);
        assertThat(result.effectiveRent()).isEqualTo(249_000L);
    }

    @Test
    @DisplayName("배타 관계인 지원금은 유리한 쪽만 반영한다")
    void should_apply_only_recommended_support() {
        EffectiveRentResult result = calculator.calculate(new EffectiveRentRequest(
                600_000,
                0,
                12,
                42_000_000,
                List.of(
                        option(RentSupportType.HOUSING_BENEFIT, 300_000, 12),
                        option(RentSupportType.YOUTH_RENT_NATIONAL, 200_000, 24)),
                true,
                true));

        // 청년월세 480만 > 주거급여 360만. 둘 다 더하면 안 된다
        assertThat(result.support().options()).hasSize(1);
        assertThat(result.monthlySupport()).isEqualTo(200_000L);
    }

    @Test
    @DisplayName("실질 월세는 0 아래로 내려가지 않는다")
    void should_not_go_negative() {
        EffectiveRentResult result = calculator.calculate(new EffectiveRentRequest(
                150_000, 0, 12, 30_000_000, List.of(option(RentSupportType.HOUSING_BENEFIT, 300_000, 12)), true, true));

        assertThat(result.effectiveRent()).isZero();
    }

    @Test
    @DisplayName("공제 대상이 아니면 지원금만 반영한다")
    void should_ignore_tax_credit_when_not_eligible() {
        EffectiveRentResult result = calculator.calculate(new EffectiveRentRequest(
                500_000,
                0,
                12,
                42_000_000,
                List.of(option(RentSupportType.YOUTH_RENT_NATIONAL, 200_000, 12)),
                true,
                false));

        assertThat(result.taxCredit().eligible()).isFalse();
        assertThat(result.monthlyTaxCredit()).isZero();
        assertThat(result.effectiveRent()).isEqualTo(300_000L);
    }
}
