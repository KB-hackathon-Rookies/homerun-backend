package com.homerun.rent;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 주거급여 판정 결과.
 *
 * @param verdict 3단계 판정
 * @param youthSeparatePayment 청년 분리지급 대상인가
 * @param rentCeiling 기준임대료 상한(원)
 * @param expectedBenefit 예상 지급액(원). 기준임대료와 실제 임차료 중 작은 쪽
 * @param reasons 판정 근거와 확인이 필요한 사항
 */
@Schema(description = "주거급여 판정 결과")
public record HousingBenefitResult(
        Verdict verdict, boolean youthSeparatePayment, long rentCeiling, long expectedBenefit, List<String> reasons) {

    public HousingBenefitResult {
        reasons = List.copyOf(reasons);
    }
}
