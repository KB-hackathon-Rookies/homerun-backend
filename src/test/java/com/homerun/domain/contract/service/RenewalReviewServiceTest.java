package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.contract.dto.response.RenewalEligibilityResponse;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.policy.dto.response.JeonsePolicyVerdictListResponse;
import com.homerun.domain.policy.dto.response.PolicyVerdictResponse;
import com.homerun.domain.policy.service.JeonsePolicyVerdictService;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** FR-H9-03·BR-30. 엔진 재판정 재사용, 버팀목만 필터, 순자산 여유액(모름·초과 포함)을 본다. */
class RenewalReviewServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;
    private static final long CAP = 345_000_000L; // FCT-170 순자산 상한 3.45억

    private final JeonsePolicyVerdictService verdictService = mock(JeonsePolicyVerdictService.class);
    private final PlanInputRepository planInputs = mock(PlanInputRepository.class);
    private final FactRegistry facts = mock(FactRegistry.class);
    private final RenewalReviewService service = new RenewalReviewService(verdictService, planInputs, facts);

    private PolicyVerdictResponse verdict(String code, PolicyVerdictResult now, PolicyVerdictResult prev) {
        return new PolicyVerdictResponse(
                code, code, now, 11, List.of(), List.of(), List.of(), null, prev, List.of("NET_ASSET_CAP"));
    }

    private void given(List<PolicyVerdictResponse> results, Long netAssets) {
        when(verdictService.evaluate(MEMBER_ID, PLAN_ID))
                .thenReturn(new JeonsePolicyVerdictListResponse(PLAN_ID, results, Instant.now()));
        when(facts.require("FCT-170"))
                .thenReturn(new Fact("FCT-170", "순자산", new BigDecimal(CAP), "원", "3.45억", "url", false));
        PlanInput input = mock(PlanInput.class);
        when(input.getNetAssets()).thenReturn(netAssets);
        when(planInputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));
    }

    @Test
    void should_computeHeadroom_andPassThroughVerdictChange_whenWithinCap() {
        given(
                List.of(verdict("JEONSE-YOUTH-BEOTIMMOK", PolicyVerdictResult.FAIL, PolicyVerdictResult.PASS)),
                300_000_000L);

        RenewalEligibilityResponse r = service.review(MEMBER_ID, PLAN_ID);

        assertThat(r.netAssetCap()).isEqualTo(CAP);
        assertThat(r.currentNetAsset()).isEqualTo(300_000_000L);
        assertThat(r.netAssetHeadroom()).isEqualTo(45_000_000L); // 3.45억 - 3억
        assertThat(r.netAssetExceeded()).isFalse();
        // 초기 PASS → 재심사 FAIL: 자격을 잃었다는 변화가 그대로 전달된다.
        assertThat(r.policies()).singleElement().satisfies(p -> {
            assertThat(p.verdict()).isEqualTo(PolicyVerdictResult.FAIL);
            assertThat(p.previousVerdict()).isEqualTo(PolicyVerdictResult.PASS);
            assertThat(p.changed()).isTrue();
        });
    }

    @Test
    void should_markExceeded_andNegativeHeadroom_whenOverCap() {
        given(
                List.of(verdict("JEONSE-YOUTH-BEOTIMMOK", PolicyVerdictResult.FAIL, PolicyVerdictResult.FAIL)),
                400_000_000L);

        RenewalEligibilityResponse r = service.review(MEMBER_ID, PLAN_ID);

        assertThat(r.netAssetExceeded()).isTrue();
        assertThat(r.netAssetHeadroom()).isEqualTo(-55_000_000L);
        assertThat(r.policies())
                .singleElement()
                .satisfies(p -> assertThat(p.changed()).isFalse());
    }

    @Test
    void should_keepHeadroomNull_whenNetAssetUnknown() {
        // 모름을 0 으로 채우면 여유 3.45억으로 통과처럼 보인다 -- null 로 둔다.
        given(List.of(verdict("JEONSE-YOUTH-BEOTIMMOK", PolicyVerdictResult.NEED_INFO, null)), null);

        RenewalEligibilityResponse r = service.review(MEMBER_ID, PLAN_ID);

        assertThat(r.currentNetAsset()).isNull();
        assertThat(r.netAssetHeadroom()).isNull();
        assertThat(r.netAssetExceeded()).isFalse();
    }

    @Test
    void should_filterToBeotimmokOnly() {
        given(
                List.of(
                        verdict("JEONSE-YOUTH-BEOTIMMOK", PolicyVerdictResult.PASS, PolicyVerdictResult.PASS),
                        verdict("JEONSE-GENERAL-BEOTIMMOK", PolicyVerdictResult.PASS, PolicyVerdictResult.PASS),
                        verdict("JEONSE-SEOUL-INTEREST-SUPPORT", PolicyVerdictResult.PASS, PolicyVerdictResult.PASS)),
                300_000_000L);

        RenewalEligibilityResponse r = service.review(MEMBER_ID, PLAN_ID);

        assertThat(r.policies())
                .extracting(RenewalEligibilityResponse.PolicyReview::policyCode)
                .containsExactly("JEONSE-YOUTH-BEOTIMMOK", "JEONSE-GENERAL-BEOTIMMOK");
    }
}
