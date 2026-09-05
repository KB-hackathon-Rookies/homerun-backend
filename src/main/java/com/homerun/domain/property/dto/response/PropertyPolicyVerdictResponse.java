package com.homerun.domain.property.dto.response;

import com.homerun.domain.policy.entity.Policy;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import com.homerun.domain.property.entity.PropertyPolicyVerdict;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 매물 하나에 대한 상품 하나의 판정(DR-10).
 *
 * @param failCodes 미충족 조건 코드 전부. 첫 실패에서 멈추지 않는다(BR-12)
 * @param failStep 명세서 2-1 의 STEP 1~4 중 어디서 걸렸는가. 통과했거나 사람 조건에서 걸렸으면 null
 */
@Schema(description = "매물 x 상품 판정")
public record PropertyPolicyVerdictResponse(
        String policyCode, String policyName, PolicyVerdictResult status, List<String> failCodes, Short failStep) {

    public PropertyPolicyVerdictResponse {
        failCodes = List.copyOf(failCodes);
    }

    public static PropertyPolicyVerdictResponse from(PropertyPolicyVerdict verdict, Policy policy) {
        return new PropertyPolicyVerdictResponse(
                policy == null ? null : policy.getCode(),
                policy == null ? null : policy.getName(),
                verdict.getStatus(),
                verdict.getFailCodes(),
                verdict.getFailStep());
    }
}
