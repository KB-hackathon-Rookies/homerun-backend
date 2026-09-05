package com.homerun.domain.policy.model;

import com.homerun.domain.policy.entity.PolicyVerdict;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import java.util.List;

/**
 * 판정을 저장한 결과와, 재판정이면 직전 상태와의 비교(VER-01-05)까지 함께 담는다.
 *
 * @param previousVerdict 이번이 첫 판정이면 null
 * @param changedConditionCodes 직전 판정과 비교해 충족 여부가 바뀐 조건 코드들. 첫 판정이면 비어 있다
 */
public record SavedVerdict(
        PolicyVerdict verdict, PolicyVerdictResult previousVerdict, List<String> changedConditionCodes) {}
