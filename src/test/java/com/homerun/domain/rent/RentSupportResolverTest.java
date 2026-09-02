package com.homerun.domain.rent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RentSupportResolverTest {

    private final RentSupportResolver resolver = new RentSupportResolver();

    private static RentSupportOption option(RentSupportType type, long monthly, int months) {
        return new RentSupportOption(type, monthly, months, false);
    }

    @Test
    @DisplayName("주거급여 수급자는 청년월세 특별지원을 함께 받지 못한다")
    void should_exclude_youth_rent_when_housing_benefit() {
        assertThat(resolver.conflicts(RentSupportType.HOUSING_BENEFIT, RentSupportType.YOUTH_RENT_NATIONAL))
                .isTrue();
    }

    @Test
    @DisplayName("국토부 청년월세와 지자체 월세지원은 동시 수령이 불가하다")
    void should_exclude_local_when_national_youth_rent() {
        assertThat(resolver.conflicts(RentSupportType.YOUTH_RENT_NATIONAL, RentSupportType.YOUTH_RENT_LOCAL))
                .isTrue();
    }

    @Test
    @DisplayName("주거급여와 지자체 지원은 배타 근거가 없어 막지 않는다")
    void should_not_exclude_pairs_without_source() {
        assertThat(resolver.conflicts(RentSupportType.HOUSING_BENEFIT, RentSupportType.YOUTH_RENT_LOCAL))
                .isFalse();
    }

    @Test
    @DisplayName("배타 관계인 조합은 후보에서 빠진다")
    void should_drop_conflicting_combinations() {
        List<RentSupportCombination> combinations = resolver.resolve(List.of(
                option(RentSupportType.HOUSING_BENEFIT, 300_000, 12),
                option(RentSupportType.YOUTH_RENT_NATIONAL, 200_000, 24)));

        assertThat(combinations).hasSize(2);
        assertThat(combinations).allSatisfy(c -> assertThat(c.options()).hasSize(1));
    }

    @Test
    @DisplayName("총액이 큰 쪽을 추천한다. 월 지원액이 작아도 기간이 길면 유리할 수 있다")
    void should_recommend_by_total_not_monthly() {
        // 주거급여 30만 × 12 = 360만 / 청년월세 20만 × 24 = 480만
        RentSupportCombination recommended = resolver.recommend(List.of(
                option(RentSupportType.HOUSING_BENEFIT, 300_000, 12),
                option(RentSupportType.YOUTH_RENT_NATIONAL, 200_000, 24)));

        assertThat(recommended.options())
                .extracting(RentSupportOption::type)
                .containsExactly(RentSupportType.YOUTH_RENT_NATIONAL);
        assertThat(recommended.totalAmount()).isEqualTo(4_800_000L);
        assertThat(recommended.recommended()).isTrue();
    }

    @Test
    @DisplayName("배타가 없는 조합은 함께 받는 쪽이 추천된다")
    void should_combine_when_no_conflict() {
        RentSupportCombination recommended = resolver.recommend(List.of(
                option(RentSupportType.HOUSING_BENEFIT, 300_000, 12),
                option(RentSupportType.YOUTH_RENT_LOCAL, 100_000, 12)));

        assertThat(recommended.options()).hasSize(2);
        assertThat(recommended.totalAmount()).isEqualTo(4_800_000L);
        assertThat(recommended.monthlyAmount()).isEqualTo(400_000L);
    }

    @Test
    @DisplayName("금액 순으로만 고르면 놓치는 조합을 찾아낸다")
    void should_find_optimal_set_not_greedy() {
        // 청년월세가 단일 최대지만, 주거급여 + 지자체 조합이 총액에서 앞선다
        RentSupportCombination recommended = resolver.recommend(List.of(
                option(RentSupportType.YOUTH_RENT_NATIONAL, 200_000, 24),
                option(RentSupportType.HOUSING_BENEFIT, 300_000, 12),
                option(RentSupportType.YOUTH_RENT_LOCAL, 150_000, 12)));

        assertThat(recommended.options())
                .extracting(RentSupportOption::type)
                .containsExactlyInAnyOrder(RentSupportType.HOUSING_BENEFIT, RentSupportType.YOUTH_RENT_LOCAL);
        assertThat(recommended.totalAmount()).isEqualTo(5_400_000L);
    }

    @Test
    @DisplayName("받을 수 있는 지원금이 없으면 빈 조합을 준다")
    void should_return_empty_combination_when_nothing_eligible() {
        RentSupportCombination recommended = resolver.recommend(List.of());

        assertThat(recommended.options()).isEmpty();
        assertThat(recommended.totalAmount()).isZero();
        assertThat(recommended.monthlyAmount()).isZero();
    }
}
