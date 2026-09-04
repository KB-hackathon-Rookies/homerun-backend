package com.homerun.domain.rent.dto.response;

import com.homerun.domain.rent.type.Verdict;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 주거급여 판정 결과.
 *
 * @param verdict 3단계 판정
 * @param youthSeparatePayment 청년 분리지급 대상인가
 * @param rentCeiling 기준임대료 상한(원)
 * @param benefitCeiling 받을 수 있는 최대 금액(원). 실제 지급액이 아니다
 *     <p>소득인정액에 따른 자기부담분 차감식은 공식 근거를 확보하지 못해 반영하지 않았다.
 *     상한까지만 안내하고 정확한 금액은 신청기관에서 확인하도록 한다. 대상이 아니면 0 이다.
 * @param provisional 판정에 쓴 기준값 중 REVIEW 등급이 있어 바뀔 수 있는가(#42 — 7인 이상
 *     소득인정액 선정 기준이 여기 해당한다)
 * @param reasons 판정 근거와 확인이 필요한 사항
 */
@Schema(description = "주거급여 판정 결과")
public record HousingBenefitResult(
        Verdict verdict,
        boolean youthSeparatePayment,
        long rentCeiling,
        long benefitCeiling,
        boolean provisional,
        List<String> reasons) {

    public HousingBenefitResult {
        reasons = List.copyOf(reasons);
    }
}
