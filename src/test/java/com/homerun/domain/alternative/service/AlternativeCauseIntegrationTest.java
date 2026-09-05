package com.homerun.domain.alternative.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.alternative.dto.response.CausesResponse;
import com.homerun.domain.policy.service.JeonsePolicyVerdictService;
import com.homerun.domain.policy.type.RejectionReasonCategory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code RejectionReasonRepository.findByVerdictId}(읽기)는 이 서비스가 처음 쓴다 —
 * {@code JeonsePolicyVerdictService}는 저장만 하고 응답은 저장 전 객체로 조립해서, 실제
 * Postgres에서 조회 경로가 진짜로 이어지는지는 여기서만 확인된다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AlternativeCauseIntegrationTest {

    private static final List<String> RETURN_GUARANTEE_CODES =
            List.of("RETURN-GUARANTEE-HUG", "RETURN-GUARANTEE-HF", "RETURN-GUARANTEE-SGI");

    private final JeonsePolicyVerdictService verdictService;
    private final AlternativeCauseService causeService;
    private final EntityManager em;

    private Long memberId;
    private Long planId;

    AlternativeCauseIntegrationTest(
            @Autowired JeonsePolicyVerdictService verdictService,
            @Autowired AlternativeCauseService causeService,
            @Autowired EntityManager em) {
        this.verdictService = verdictService;
        this.causeService = causeService;
        this.em = em;
    }

    @Test
    void should_readPersistedCause_when_returnGuaranteeFailsPriceRatioAgainstRealPostgres() {
        setUpPlan();
        RETURN_GUARANTEE_CODES.forEach(this::activatePriceRatioRule);
        // 공시가 5억 × 1.26 = 6억 3천 < 보증금 7억 → 126% 룰 위반 → FAIL.
        Long badPropertyId = ((Number) em.createNativeQuery(
                                "INSERT INTO property (plan_id, deposit, official_price) VALUES (:pid, 700000000, 500000000) RETURNING id")
                        .setParameter("pid", planId)
                        .getSingleResult())
                .longValue();

        verdictService.evaluateReturnGuarantees(memberId, planId, badPropertyId);
        em.flush();
        em.clear();

        CausesResponse response = causeService.causes(memberId, planId);

        var cause = response.causes().stream()
                .filter(c -> c.policyCode().equals("RETURN-GUARANTEE-HUG"))
                .findFirst()
                .orElseThrow();
        assertThat(cause.reasonCode()).isEqualTo("PRICE_RATIO_126");
        assertThat(cause.category()).isEqualTo(RejectionReasonCategory.HOUSE);
        assertThat(cause.alternativePolicyCode()).isEqualTo("RETURN-GUARANTEE-SGI");
    }

    private void setUpPlan() {
        memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, nickname)
                        VALUES ('KAKAO', 'alt-cause-test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        planId = ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
        em.createNativeQuery("INSERT INTO plan_input (plan_id, financial_data_confirmed) VALUES (:pid, true)")
                .setParameter("pid", planId)
                .executeUpdate();
    }

    private void activatePriceRatioRule(String policyCode) {
        em.createNativeQuery("""
                        INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, reviewed_by)
                        SELECT id, 2,
                            '{"operator":"AND","conditions":[
                                {"code":"PRICE_RATIO_126","field":"official_price",
                                 "op":"deposit_lte_price_times_fact","fact_code":"FCT-054"}
                            ]}'::jsonb,
                            'ACTIVE', '2026-09-04', 'integration-test'
                        FROM policy WHERE code = :code
                        """).setParameter("code", policyCode).executeUpdate();
    }
}
