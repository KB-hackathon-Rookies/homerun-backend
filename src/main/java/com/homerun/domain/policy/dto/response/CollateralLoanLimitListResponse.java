package com.homerun.domain.policy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 일반 전세대출의 담보별 한도 묶음(BR-19 · FR-D4-12 · FR-P4-06).
 *
 * <p>1루 카드는 이 최소~최대 범위로 표시하고("1억 4,400만원 ~ 1억 6,200만원, 담보 방식에 따라
 * 달라져요"), 2-6 상담에서 담보가 확정되면 단일 값으로 갱신한다.
 *
 * @param minLimit 담보별 한도 중 최소. 하나도 계산 못 하면 null
 * @param maxLimit 담보별 한도 중 최대. 하나도 계산 못 하면 null
 */
@Schema(description = "일반 전세대출 담보별 한도. 1루 카드는 min~max 범위로 표시한다")
public record CollateralLoanLimitListResponse(
        List<CollateralLoanLimitResponse> collaterals, Long minLimit, Long maxLimit) {

    public CollateralLoanLimitListResponse {
        collaterals = List.copyOf(collaterals);
    }
}
