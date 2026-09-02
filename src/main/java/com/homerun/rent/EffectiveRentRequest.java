package com.homerun.rent;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 실질 월세 계산 입력.
 *
 * @param monthlyRent 월세(원)
 * @param managementFee 월 관리비(원). 월세와 별도로 나가는 고정비다
 * @param months 계약 기간 중 해당 연도 월수
 * @param annualSalary 총급여(원)
 * @param eligibleSupports 판정 결과 받을 수 있는 지원금들
 * @param homeless 무주택 여부
 * @param residentRegistered 전입신고 완료 여부
 */
@Schema(description = "실질 월세 계산 입력")
public record EffectiveRentRequest(
        long monthlyRent,
        long managementFee,
        int months,
        long annualSalary,
        List<RentSupportOption> eligibleSupports,
        boolean homeless,
        boolean residentRegistered) {

    public EffectiveRentRequest {
        eligibleSupports = List.copyOf(eligibleSupports);
    }
}
