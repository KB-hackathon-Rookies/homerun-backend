package com.homerun.rent;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 받을 수 있다고 판정된 지원금 하나.
 *
 * @param type 지원금 종류
 * @param monthlyAmount 월 지원액(원)
 * @param months 지원 개월 수
 * @param provisional 기준 수치가 확정 전이라 바뀔 수 있는가
 */
@Schema(description = "수급 가능한 월세 지원금")
public record RentSupportOption(RentSupportType type, long monthlyAmount, int months, boolean provisional) {

    public RentSupportOption {
        if (monthlyAmount < 0) {
            throw new IllegalArgumentException("월 지원액은 음수가 될 수 없다: " + monthlyAmount);
        }
        if (months < 0) {
            throw new IllegalArgumentException("지원 개월 수는 음수가 될 수 없다: " + months);
        }
    }

    /** 지원 기간 전체로 받는 총액. 상호배타 그룹에서 유리한 쪽을 고를 때의 기준이다. */
    public long totalAmount() {
        return monthlyAmount * months;
    }

    /** 한 해에 받는 금액. 세액공제 차감에 쓴다. */
    public long amountWithinYear() {
        return monthlyAmount * Math.min(months, 12);
    }
}
