package com.homerun.domain.rent.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 동시에 받을 수 있는 지원금 조합 하나.
 *
 * @param options 이 조합에 포함된 지원금
 * @param totalAmount 조합 전체의 총 수령액(원)
 * @param recommended 유리한 쪽으로 추천되는 조합인가
 */
@Schema(description = "동시 수급 가능한 지원금 조합")
public record RentSupportCombination(List<RentSupportOption> options, long totalAmount, boolean recommended) {

    public RentSupportCombination {
        options = List.copyOf(options);
    }

    public RentSupportCombination asRecommended() {
        return new RentSupportCombination(options, totalAmount, true);
    }

    /** 이 조합으로 한 해에 받는 지원금. 세액공제 차감에 쓴다. */
    public long amountWithinYear() {
        return options.stream().mapToLong(RentSupportOption::amountWithinYear).sum();
    }

    /** 이 조합의 월 지원액 합계. 실질 월세 계산에 쓴다. */
    public long monthlyAmount() {
        return options.stream().mapToLong(RentSupportOption::monthlyAmount).sum();
    }
}
