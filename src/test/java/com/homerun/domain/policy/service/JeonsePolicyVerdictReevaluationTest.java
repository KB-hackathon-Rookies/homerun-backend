package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.policy.dto.response.JeonsePolicyVerdictListResponse;
import com.homerun.domain.policy.dto.response.PolicyVerdictResponse;
import com.homerun.domain.policy.model.ConditionResult;
import com.homerun.domain.policy.model.ExpectedEstimate;
import com.homerun.domain.policy.repository.PolicyRepository;
import com.homerun.domain.policy.repository.PolicyRuleRepository;
import com.homerun.domain.policy.repository.PolicyVerdictRepository;
import com.homerun.domain.policy.repository.RejectionReasonRepository;
import com.homerun.domain.policy.repository.VerdictBasisRepository;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import com.homerun.domain.property.repository.PropertyRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재판정 시 직전 판정과 비교해 무엇이 바뀌었는지 표시하는지 본다(VER-01-04 재판정,
 * VER-01-05 판정 변경 표시). {@link PolicyRuleEngine}은 mock 하고 실제 저장소를 써서, 두 번째
 * {@code evaluate} 호출이 첫 번째 호출이 남긴 {@code policy_verdict}·{@code verdict_basis}를
 * 실제로 다시 읽는지까지 확인한다 — 순수 mock 저장소로는 auto-increment id가 없어 여러 정책의
 * "직전 상태"를 서로 구분해 담을 수 없다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JeonsePolicyVerdictReevaluationTest {

    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

    /** evaluateGuaranteeFeeSupport()가 도는 유일한 정책 코드 — plan_input만 보고 property가
     * 필요 없어 이 테스트에서 가장 단순하게 재판정을 두 번 돌릴 수 있다. */
    private static final String POLICY_CODE = "RETURN-GUARANTEE-FEE-SUPPORT";

    private final EntityManager em;
    private final PolicyRuleEngine engine = mock(PolicyRuleEngine.class);
    private final JeonsePolicyVerdictService service;

    private Long ownerId;
    private Long planId;

    JeonsePolicyVerdictReevaluationTest(
            @Autowired PlanRepository plans,
            @Autowired PlanInputRepository inputs,
            @Autowired PropertyRepository properties,
            @Autowired PolicyRepository policies,
            @Autowired PolicyRuleRepository rules,
            @Autowired PolicyVerdictRepository verdicts,
            @Autowired VerdictBasisRepository basisRepository,
            @Autowired RejectionReasonRepository rejectionReasons,
            @Autowired EntityManager em) {
        this.em = em;
        this.service = new JeonsePolicyVerdictService(
                plans,
                inputs,
                properties,
                policies,
                rules,
                verdicts,
                basisRepository,
                rejectionReasons,
                engine,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @BeforeEach
    void setUp() {
        ownerId = (Long) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, name)
                        VALUES ('KAKAO', 'test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult();
        planId = (Long)
                em.createNativeQuery("INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", ownerId)
                        .getSingleResult();
        em.createNativeQuery("INSERT INTO plan_input (plan_id) VALUES (:planId)")
                .setParameter("planId", planId)
                .executeUpdate();
        em.flush();
        em.clear();
        // POLICY_CODE(RETURN-GUARANTEE-FEE-SUPPORT)는 V26/V31 시드에 정책과 ACTIVE
        // policy_rule이 이미 들어있다 — 여기서 새로 만들면 code UNIQUE 제약에 걸린다.
    }

    private PolicyVerdictResponse evaluateOnce(List<ConditionResult> result) {
        when(engine.evaluate(any(), any(), any())).thenReturn(result);
        when(engine.estimate(any(), any(), any(), any())).thenReturn(ExpectedEstimate.empty());
        JeonsePolicyVerdictListResponse response = service.evaluateGuaranteeFeeSupport(ownerId, planId);
        return response.results().stream()
                .filter(r -> r.policyCode().equals(POLICY_CODE))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("처음 판정하는 정책은 previousVerdict가 없다")
    void should_haveNoPreviousVerdict_onFirstEvaluation() {
        PolicyVerdictResponse response =
                evaluateOnce(List.of(new ConditionResult("INCOME_LIMIT", "소득 기준", "연 5,000만원 이하", true, null, null)));

        assertThat(response.verdict()).isEqualTo(PolicyVerdictResult.PASS);
        assertThat(response.previousVerdict()).isNull();
        assertThat(response.changedConditionCodes()).isEmpty();
    }

    @Test
    @DisplayName("재판정으로 결과가 바뀌면 직전 판정과 바뀐 조건을 함께 보여준다")
    void should_showPreviousVerdictAndChangedConditions_when_reevaluated() {
        evaluateOnce(List.of(new ConditionResult("INCOME_LIMIT", "소득 기준", "연 5,000만원 이하", true, null, null)));
        em.flush();
        em.clear();

        when(engine.evaluate(any(), any(), any()))
                .thenReturn(List.of(new ConditionResult("INCOME_LIMIT", "소득 기준", "연 5,000만원 이하", false, null, null)));
        JeonsePolicyVerdictListResponse second = service.evaluateGuaranteeFeeSupport(ownerId, planId);
        PolicyVerdictResponse response = second.results().stream()
                .filter(r -> r.policyCode().equals(POLICY_CODE))
                .findFirst()
                .orElseThrow();

        assertThat(response.verdict()).isEqualTo(PolicyVerdictResult.FAIL);
        assertThat(response.previousVerdict()).isEqualTo(PolicyVerdictResult.PASS);
        assertThat(response.changedConditionCodes()).containsExactly("INCOME_LIMIT");
    }

    @Test
    @DisplayName("재판정해도 조건이 그대로면 바뀐 조건이 없다")
    void should_haveNoChangedConditions_when_reevaluationKeepsSameResult() {
        evaluateOnce(List.of(new ConditionResult("INCOME_LIMIT", "소득 기준", "연 5,000만원 이하", true, null, null)));
        em.flush();
        em.clear();

        when(engine.evaluate(any(), any(), any()))
                .thenReturn(List.of(new ConditionResult("INCOME_LIMIT", "소득 기준", "연 5,000만원 이하", true, null, null)));
        JeonsePolicyVerdictListResponse second = service.evaluateGuaranteeFeeSupport(ownerId, planId);
        PolicyVerdictResponse response = second.results().stream()
                .filter(r -> r.policyCode().equals(POLICY_CODE))
                .findFirst()
                .orElseThrow();

        assertThat(response.verdict()).isEqualTo(PolicyVerdictResult.PASS);
        assertThat(response.previousVerdict()).isEqualTo(PolicyVerdictResult.PASS);
        assertThat(response.changedConditionCodes()).isEmpty();
    }
}
