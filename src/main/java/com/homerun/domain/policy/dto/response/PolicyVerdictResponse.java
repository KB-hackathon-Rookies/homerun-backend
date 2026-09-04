package com.homerun.domain.policy.dto.response;

import com.homerun.domain.policy.type.PolicyVerdictResult;
import java.util.List;

/**
 * @param ruleVersion 판정에 쓴 policy_rule 버전. 검수된 조건식이 없어 판정을 못 했으면 null이다
 * @param missingFields NEED_INFO 로 빠진 조건 코드들 — API 공통계약의 missingFields[] (Notion
 *     API 명세서 "판정 응답의 공통 계약")
 */
public record PolicyVerdictResponse(
        String policyCode,
        String policyName,
        PolicyVerdictResult verdict,
        Integer ruleVersion,
        List<ConditionBasisResponse> basis,
        List<String> missingFields,
        List<RejectionReasonResponse> rejectionReasons,
        LoanEstimateResponse estimate) {}
