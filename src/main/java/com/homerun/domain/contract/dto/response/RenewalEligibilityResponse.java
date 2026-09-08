package com.homerun.domain.contract.dto.response;

import com.homerun.domain.policy.type.PolicyVerdictResult;
import java.util.List;

/**
 * 갱신 시점 버팀목 자격 재심사 결과(FR-H9-03·BR-30).
 *
 * @param netAssetCap 순자산 상한(FCT-170, 원). 갱신 때 이 값을 넘으면 자격을 잃는다.
 * @param currentNetAsset 사용자가 입력한 현재 순자산. 모르면 null 이다 — 0 으로 채우지 않는다.
 * @param netAssetHeadroom 상한까지 남은 여유액(cap − current). 모르면 null, 음수면 이미 초과다.
 * @param netAssetExceeded 현재 순자산이 상한을 넘었는지. 모르면 false(초과라고 단정하지 않는다).
 * @param netAssetSourceUrl 순자산 상한의 공식 근거 링크
 * @param policies 버팀목 정책별 재판정 결과
 */
public record RenewalEligibilityResponse(
        long netAssetCap,
        Long currentNetAsset,
        Long netAssetHeadroom,
        boolean netAssetExceeded,
        String netAssetSourceUrl,
        List<PolicyReview> policies) {

    /**
     * @param previousVerdict 이번 재판정 직전(주로 초기 2루 판정)의 결과. 처음이면 null.
     * @param changed 직전과 판정이 달라졌는지(= verdict != previousVerdict)
     */
    public record PolicyReview(
            String policyCode,
            String policyName,
            PolicyVerdictResult verdict,
            PolicyVerdictResult previousVerdict,
            boolean changed,
            List<String> changedConditionCodes) {}
}
