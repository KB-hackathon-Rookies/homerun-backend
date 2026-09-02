package com.homerun.domain.rent;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 세액공제 계산 입력.
 *
 * @param monthlyRent 월세(원). 관리비는 포함하지 않는다
 * @param months 해당 연도에 월세를 낸 개월 수
 * @param annualSalary 총급여(원)
 * @param supportWithinYear 같은 해에 받은 월세 지원금(원)
 * @param homeless 무주택 여부
 * @param residentRegistered 전입신고 완료 여부
 */
@Schema(description = "월세 세액공제 계산 입력")
public record TaxCreditRequest(
        @Min(value = 0, message = "월세는 0원 이상이어야 한다") long monthlyRent,

        @Min(value = 0, message = "개월 수는 0 이상이어야 한다") @Max(value = 12, message = "개월 수는 12 이하여야 한다")
        int months,

        @Min(value = 0, message = "총급여는 0원 이상이어야 한다") long annualSalary,
        @Min(value = 0, message = "지원금은 0원 이상이어야 한다") long supportWithinYear,
        boolean homeless,
        boolean residentRegistered) {

    public TaxCreditRequest {
        if (monthlyRent < 0 || annualSalary < 0 || supportWithinYear < 0) {
            throw new IllegalArgumentException("금액은 음수가 될 수 없다");
        }
        if (months < 0 || months > 12) {
            throw new IllegalArgumentException("개월 수는 0에서 12 사이여야 한다: " + months);
        }
    }

    long annualRent() {
        return monthlyRent * months;
    }
}
