package com.homerun.domain.rent;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class HousingBenefitEvaluatorTest {

    private final HousingBenefitEvaluator evaluator;

    HousingBenefitEvaluatorTest(@Autowired HousingBenefitEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    private static HousingBenefitRequest single(long income, long rent) {
        return new HousingBenefitRequest(1, income, rent, 26, false, true, true);
    }

    @Test
    @DisplayName("1인가구 소득인정액이 기준 이하면 대상이다")
    void should_be_eligible_under_income_threshold() {
        assertThat(evaluator.evaluate(single(1_000_000, 400_000)).verdict()).isEqualTo(Verdict.ELIGIBLE);
    }

    @Test
    @DisplayName("소득인정액이 기준을 넘으면 대상이 아니다")
    void should_be_ineligible_over_income_threshold() {
        // FCT-039 1,230,834원
        assertThat(evaluator.evaluate(single(1_230_835, 400_000)).verdict()).isEqualTo(Verdict.INELIGIBLE);
    }

    @Test
    @DisplayName("지급액은 기준임대료와 실제 임차료 중 작은 쪽이다")
    void should_cap_benefit_at_rent_ceiling() {
        // 서울 1인 기준임대료 369,000원
        assertThat(evaluator.evaluate(single(1_000_000, 500_000)).expectedBenefit())
                .isEqualTo(369_000L);
        assertThat(evaluator.evaluate(single(1_000_000, 300_000)).expectedBenefit())
                .isEqualTo(300_000L);
    }

    @Test
    @DisplayName("다인 가구는 소득 기준이 없어 불가가 아니라 추가확인이다")
    void should_need_check_for_multi_person_household() {
        HousingBenefitResult result =
                evaluator.evaluate(new HousingBenefitRequest(3, 9_000_000, 400_000, 26, false, true, true));

        assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_CHECK);
        assertThat(result.rentCeiling()).isEqualTo(492_000L);
    }

    @Test
    @DisplayName("기준임대료가 없는 가구원 수는 추가확인으로 넘긴다")
    void should_need_check_when_ceiling_unknown() {
        HousingBenefitResult result =
                evaluator.evaluate(new HousingBenefitRequest(7, 1_000_000, 400_000, 26, false, true, true));

        assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_CHECK);
        assertThat(result.rentCeiling()).isZero();
    }

    @Test
    @DisplayName("부모가 수급 중이 아니면 청년 분리지급 대상이 아니다")
    void should_require_parent_on_benefit_for_youth_payment() {
        HousingBenefitResult result =
                evaluator.evaluate(new HousingBenefitRequest(1, 1_000_000, 400_000, 26, false, true, false));

        assertThat(result.youthSeparatePayment()).isFalse();
        assertThat(result.reasons()).anySatisfy(reason -> assertThat(reason).contains("부모 가구가 이미"));
    }

    @Test
    @DisplayName("만 30세 이상은 청년 분리지급 대상이 아니다")
    void should_exclude_age_30_and_over() {
        assertThat(evaluator
                        .evaluate(new HousingBenefitRequest(1, 1_000_000, 400_000, 30, false, true, true))
                        .youthSeparatePayment())
                .isFalse();
    }

    @Test
    @DisplayName("부모와 같은 시·군에 살면 청년 분리지급 대상이 아니다")
    void should_require_living_apart() {
        assertThat(evaluator
                        .evaluate(new HousingBenefitRequest(1, 1_000_000, 400_000, 26, false, false, true))
                        .youthSeparatePayment())
                .isFalse();
    }

    @Test
    @DisplayName("요건을 모두 채우면 청년 분리지급 대상이다")
    void should_qualify_for_youth_separate_payment() {
        HousingBenefitResult result = evaluator.evaluate(single(1_000_000, 400_000));

        assertThat(result.youthSeparatePayment()).isTrue();
        assertThat(result.verdict()).isEqualTo(Verdict.ELIGIBLE);
    }
}
