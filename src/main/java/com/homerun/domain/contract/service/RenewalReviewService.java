package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.RenewalEligibilityResponse;
import com.homerun.domain.contract.dto.response.RenewalEligibilityResponse.PolicyReview;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.policy.dto.response.PolicyVerdictResponse;
import com.homerun.domain.policy.service.JeonsePolicyVerdictService;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 갱신 시점 버팀목 자격 재심사(FR-H9-03·BR-30).
 *
 * <p>판정은 기존 판정 엔진을 그대로 재사용한다 — 갱신이라고 다른 규칙을 쓰지 않는다. 엔진이 현재
 * 입력으로 다시 판정하며 직전 판정(previousVerdict)까지 함께 돌려준다. 여기서 더하는 것은 버팀목
 * 한정 필터와, 갱신에서 특히 중요한 순자산 여유액(상한 − 현재)뿐이다. 자산은 갱신 때 늘어 상한을
 * 넘기기 쉽다.
 */
@Service
public class RenewalReviewService {

    /** 순자산 상한이 자격을 좌우하는 기금 버팀목만 재심사 대상이다. 서울시 이자지원은 구조가 달라 뺀다. */
    private static final List<String> BEOTIMMOK_CODES = List.of("JEONSE-YOUTH-BEOTIMMOK", "JEONSE-GENERAL-BEOTIMMOK");

    /** 청년 버팀목 순자산 기준 3.45억(2026 공식). CONFLICT 였던 FCT-004 를 대체한 확정값. */
    private static final String NET_ASSET_CAP_FACT = "FCT-170";

    private final JeonsePolicyVerdictService verdictService;
    private final PlanInputRepository planInputs;
    private final FactRegistry facts;

    public RenewalReviewService(
            JeonsePolicyVerdictService verdictService, PlanInputRepository planInputs, FactRegistry facts) {
        this.verdictService = verdictService;
        this.planInputs = planInputs;
        this.facts = facts;
    }

    @Transactional
    public RenewalEligibilityResponse review(Long memberId, Long planId) {
        // 소유·전세 계획 검증과 plan_input 로딩은 엔진이 안에서 한다. 없으면 여기까지 오지 않는다.
        List<PolicyVerdictResponse> results =
                verdictService.evaluate(memberId, planId).results();
        List<PolicyReview> policies = results.stream()
                .filter(r -> BEOTIMMOK_CODES.contains(r.policyCode()))
                .map(r -> new PolicyReview(
                        r.policyCode(),
                        r.policyName(),
                        r.verdict(),
                        r.previousVerdict(),
                        !Objects.equals(r.verdict(), r.previousVerdict()),
                        r.changedConditionCodes()))
                .toList();

        Fact cap = facts.require(NET_ASSET_CAP_FACT);
        long netAssetCap = cap.requireWon();
        Long current =
                planInputs.findByPlanId(planId).map(PlanInput::getNetAssets).orElse(null);
        Long headroom = current == null ? null : netAssetCap - current;
        boolean exceeded = current != null && current > netAssetCap;

        return new RenewalEligibilityResponse(netAssetCap, current, headroom, exceeded, cap.sourceUrl(), policies);
    }
}
