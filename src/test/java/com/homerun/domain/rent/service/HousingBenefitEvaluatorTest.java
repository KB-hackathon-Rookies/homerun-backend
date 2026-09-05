package com.homerun.domain.rent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.rent.dto.request.HousingBenefitRequest;
import com.homerun.domain.rent.dto.response.HousingBenefitResult;
import com.homerun.domain.rent.type.Verdict;
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

    /**
     * 일반 주거급여 경로. 만 35세라 분리지급 요건 밖이라서 신청 가구 소득으로 판정된다.
     *
     * <p>#162 이전에는 이 픽스처가 만 26세였다 — 분리지급 요건을 전부 만족해서, 일반 경로를
     * 검증하려던 테스트가 사실 전부 분리지급 케이스였고 두 경로가 갈린다는 것이 가려져 있었다.
     */
    private static HousingBenefitRequest single(long income, long rent) {
        return new HousingBenefitRequest(1, income, rent, 35, false, true, true);
    }

    /** 분리지급 경로. 연령·미혼·시·군 상이·부모 수급을 모두 채운다. */
    private static HousingBenefitRequest youthSplit(long income, long rent) {
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
        assertThat(evaluator.evaluate(single(1_000_000, 500_000)).benefitCeiling())
                .isEqualTo(369_000L);
        assertThat(evaluator.evaluate(single(1_000_000, 300_000)).benefitCeiling())
                .isEqualTo(300_000L);
    }

    @Test
    @DisplayName("3인 가구도 소득 기준이 있어 판정된다(#42)")
    void should_judge_multi_person_household_income() {
        // FCT-185(3인 소득인정액) 2,572,337원을 초과 → 대상 아님으로 확정 판정된다(추가확인 아님).
        HousingBenefitResult result =
                evaluator.evaluate(new HousingBenefitRequest(3, 9_000_000, 400_000, 35, false, true, true));

        assertThat(result.verdict()).isEqualTo(Verdict.INELIGIBLE);
        assertThat(result.rentCeiling()).isEqualTo(492_000L);
    }

    @Test
    @DisplayName("8인 이상은 소득 기준이 없어 추가확인이다(#42)")
    void should_need_check_for_income_when_householdSizeIsEightOrMore() {
        // 기준임대료(FCT-191)는 있지만 소득 기준은 7인까지만 확보돼 있다.
        HousingBenefitResult result =
                evaluator.evaluate(new HousingBenefitRequest(8, 1_000_000, 400_000, 35, false, true, true));

        assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_CHECK);
        assertThat(result.rentCeiling()).isEqualTo(768_900L);
    }

    @Test
    @DisplayName("기준임대료가 없는 가구원 수(10인 이상)는 추가확인으로 넘긴다")
    void should_need_check_when_ceiling_unknown() {
        HousingBenefitResult result =
                evaluator.evaluate(new HousingBenefitRequest(10, 1_000_000, 400_000, 35, false, true, true));

        assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_CHECK);
        assertThat(result.rentCeiling()).isZero();
    }

    @Test
    @DisplayName("7인가구 소득인정액이 기준 이하면 대상이다(#42)")
    void should_be_eligible_for_sevenPersonHousehold() {
        // FCT-189(7인 소득인정액) 4,517,542원.
        HousingBenefitResult result =
                evaluator.evaluate(new HousingBenefitRequest(7, 4_500_000, 400_000, 35, false, true, true));

        assertThat(result.verdict()).isEqualTo(Verdict.ELIGIBLE);
        assertThat(result.rentCeiling()).isEqualTo(699_000L); // 6인과 동일
    }

    @Test
    @DisplayName("8인·9인 기준임대료는 같은 값이다(#42)")
    void should_shareSameRentCeiling_forEightAndNinePersonHouseholds() {
        long eight = evaluator
                .evaluate(new HousingBenefitRequest(8, 1_000_000, 1_000_000, 35, false, true, true))
                .rentCeiling();
        long nine = evaluator
                .evaluate(new HousingBenefitRequest(9, 1_000_000, 1_000_000, 35, false, true, true))
                .rentCeiling();

        assertThat(eight).isEqualTo(nine).isEqualTo(768_900L);
    }

    @Test
    @DisplayName("7인가구 판정은 REVIEW 등급 기준값을 써서 provisional이 true다(#42)")
    void should_markProvisional_when_usingSevenPersonIncomeThreshold() {
        HousingBenefitResult result =
                evaluator.evaluate(new HousingBenefitRequest(7, 1_000_000, 400_000, 35, false, true, true));

        assertThat(result.provisional()).isTrue();
        assertThat(result.reasons()).anySatisfy(reason -> assertThat(reason).contains("바뀔 수 있다"));
    }

    @Test
    @DisplayName("1인가구 판정은 전부 CONFIRMED 등급이라 provisional이 false다")
    void should_notMarkProvisional_forSinglePersonHousehold() {
        assertThat(evaluator.evaluate(single(1_000_000, 400_000)).provisional()).isFalse();
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
    @DisplayName("요건을 모두 채우면 청년 분리지급 대상이고, 소득은 부모 가구 기준이라 추가확인이다")
    void should_qualify_for_youth_separate_payment() {
        HousingBenefitResult result = evaluator.evaluate(youthSplit(1_000_000, 400_000));

        assertThat(result.youthSeparatePayment()).isTrue();
        // 신청 가구 소득이 1인 기준 이하여도 그것으로 확정하지 않는다 — 기준이 다른 값이다.
        assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_CHECK);
        assertThat(result.reasons()).anySatisfy(reason -> assertThat(reason).contains("부모 가구 기준"));
    }

    @Test
    @DisplayName("#162 분리지급 대상자의 본인 소득이 1인 기준을 넘어도 불가로 단정하지 않는다")
    void should_notRejectYouthSplit_when_ownIncomeExceedsSinglePersonThreshold() {
        // 이것이 이 버그의 실제 피해다. FCT-039(1,230,834원)를 넘는 본인 소득으로
        // INELIGIBLE 을 내면, 부모 가구 기준으로는 대상일 수 있는 청년에게 "안 된다"고 단정하게 된다.
        HousingBenefitResult result = evaluator.evaluate(youthSplit(1_300_000, 400_000));

        assertThat(result.youthSeparatePayment()).isTrue();
        assertThat(result.verdict()).isNotEqualTo(Verdict.INELIGIBLE);
        assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_CHECK);
    }

    @Test
    @DisplayName("#162 분리지급 요건 밖이면 신청 가구 소득으로 그대로 판정한다")
    void should_stillJudgeApplicantIncome_when_notYouthSplit() {
        // 부모가 수급 중이 아니면 분리지급 대상이 아니므로 일반 경로로 판정한다.
        HousingBenefitResult belowThreshold =
                evaluator.evaluate(new HousingBenefitRequest(1, 1_000_000, 400_000, 26, false, true, false));
        HousingBenefitResult aboveThreshold =
                evaluator.evaluate(new HousingBenefitRequest(1, 1_300_000, 400_000, 26, false, true, false));

        assertThat(belowThreshold.verdict()).isEqualTo(Verdict.ELIGIBLE);
        assertThat(aboveThreshold.verdict()).isEqualTo(Verdict.INELIGIBLE);
    }

    @Test
    @DisplayName("대상이 아니면 금액을 0으로 준다")
    void should_return_zero_when_ineligible() {
        HousingBenefitResult result = evaluator.evaluate(single(1_230_835, 400_000));

        assertThat(result.verdict()).isEqualTo(Verdict.INELIGIBLE);
        assertThat(result.benefitCeiling()).isZero();
    }

    @Test
    @DisplayName("금액이 지급액이 아니라 상한임을 함께 알린다")
    void should_state_it_is_a_ceiling() {
        HousingBenefitResult result = evaluator.evaluate(single(1_000_000, 300_000));

        assertThat(result.reasons()).anySatisfy(reason -> assertThat(reason).contains("자기부담분"));
    }
}
