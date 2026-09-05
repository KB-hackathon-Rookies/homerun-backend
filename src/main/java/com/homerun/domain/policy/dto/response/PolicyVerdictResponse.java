package com.homerun.domain.policy.dto.response;

import com.homerun.domain.policy.type.PolicyVerdictResult;
import java.util.List;

/**
 * @param ruleVersion 판정에 쓴 policy_rule 버전. 검수된 조건식이 없어 판정을 못 했으면 null이다
 * @param missingFields NEED_INFO 로 빠진 조건 코드들 — API 공통계약의 missingFields[] (Notion
 *     API 명세서 "판정 응답의 공통 계약")
 * @param previousVerdict 이번 재판정 직전의 판정 결과(VER-01-05). 이 계획·정책 조합을 처음
 *     판정하는 것이면 null이다 — {@code changed}(=verdict != previousVerdict)를 프런트에서
 *     따로 계산하지 않아도 되게 원본을 그대로 남긴다.
 * @param changedConditionCodes 직전 판정과 비교해 충족 여부(isMet)가 바뀐 조건 코드들. 처음
 *     판정이면 비어 있다 — "무엇 때문에 바뀌었는지"를 조건 단위로 보여준다
 */
public record PolicyVerdictResponse(
        String policyCode,
        String policyName,
        PolicyVerdictResult verdict,
        Integer ruleVersion,
        List<ConditionBasisResponse> basis,
        List<String> missingFields,
        List<RejectionReasonResponse> rejectionReasons,
        LoanEstimateResponse estimate,
        PolicyVerdictResult previousVerdict,
        List<String> changedConditionCodes) {}
