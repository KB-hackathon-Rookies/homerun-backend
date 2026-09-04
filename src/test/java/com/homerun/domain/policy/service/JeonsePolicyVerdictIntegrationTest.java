package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.policy.entity.PolicyRule;
import com.homerun.domain.policy.model.RuleCondition;
import com.homerun.domain.policy.repository.PolicyRuleRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 Postgres 에서 rule_json JSONB 왕복과 판정 저장을 확인한다. Mock 으로는 Hibernate 의
 * JSON 매핑이 진짜로 되는지, unique 제약이 재판정에서 안 걸리는지 볼 수 없다.
 *
 * <p>#85(V22) 시드는 전부 DRAFT 라 건드리지 않고, 검수를 마쳤다고 가정한 ACTIVE 버전을
 * 이 테스트 트랜잭션 안에서만 따로 만든다(트랜잭션 롤백으로 정리된다).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JeonsePolicyVerdictIntegrationTest {

    private static final List<String> JEONSE_POLICY_CODES =
            List.of("JEONSE-YOUTH-BEOTIMMOK", "JEONSE-GENERAL-BEOTIMMOK", "JEONSE-SEOUL-INTEREST-SUPPORT");
    private static final List<String> RETURN_GUARANTEE_CODES =
            List.of("RETURN-GUARANTEE-HUG", "RETURN-GUARANTEE-HF", "RETURN-GUARANTEE-SGI");

    private final JeonsePolicyVerdictService service;
    private final PolicyRuleRepository policyRuleRepository;
    private final EntityManager em;

    private Long memberId;
    private Long planId;
    private Long youthPolicyId;
    private Long propertyId;

    JeonsePolicyVerdictIntegrationTest(
            @Autowired JeonsePolicyVerdictService service,
            @Autowired PolicyRuleRepository policyRuleRepository,
            @Autowired EntityManager em) {
        this.service = service;
        this.policyRuleRepository = policyRuleRepository;
        this.em = em;
    }

    @BeforeEach
    void setUp() {
        memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, nickname)
                        VALUES ('KAKAO', 'policy-test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        planId = ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
        em.createNativeQuery("""
                        INSERT INTO plan_input
                            (plan_id, household_homeless, householder_status, has_existing_jeonse_loan,
                             hope_deposit, financial_data_confirmed)
                        VALUES (:pid, true, 'CURRENT', false, 150000000, true)
                        """).setParameter("pid", planId).executeUpdate();

        youthPolicyId = ((Number) em.createNativeQuery("SELECT id FROM policy WHERE code = 'JEONSE-YOUTH-BEOTIMMOK'")
                        .getSingleResult())
                .longValue();

        // 공시가 5억 × 1.26 = 6억 3천 = 보증금과 정확히 같음 → 126% 룰 충족(<=).
        propertyId = ((Number) em.createNativeQuery(
                                "INSERT INTO property (plan_id, deposit, official_price) VALUES (:pid, 630000000, 500000000) RETURNING id")
                        .setParameter("pid", planId)
                        .getSingleResult())
                .longValue();

        JEONSE_POLICY_CODES.forEach(this::activateSimpleReviewedRule);
        RETURN_GUARANTEE_CODES.forEach(this::activatePriceRatioRule);
    }

    @Test
    void should_deserializeSeededDraftRuleJson_when_readFromRealPostgres() {
        Long draftRuleId = ((Number)
                        em.createNativeQuery("SELECT id FROM policy_rule WHERE policy_id = :pid AND status = 'DRAFT'")
                                .setParameter("pid", youthPolicyId)
                                .getSingleResult())
                .longValue();

        PolicyRule draft = policyRuleRepository.findById(draftRuleId).orElseThrow();

        assertThat(draft.getRuleJson().conditions())
                .extracting(RuleCondition::code)
                .containsExactly(
                        "HOUSEHOLD_HOMELESS",
                        "HOUSEHOLDER_STATUS",
                        "NO_DUPLICATE_LOAN",
                        "AGE_UPPER_BOUND",
                        "INCOME_CAP",
                        "NET_ASSET_CAP",
                        "DEPOSIT_CAP");
    }

    @Test
    void should_persistVerdictAndBasis_when_evaluatingAgainstRealPostgres() {
        service.evaluate(memberId, planId);
        em.flush();
        em.clear();

        @SuppressWarnings("unchecked")
        List<Object[]> rows =
                em.createNativeQuery("""
                        SELECT p.code, v.verdict
                        FROM policy_verdict v JOIN policy p ON p.id = v.policy_id
                        WHERE v.plan_id = :pid
                        ORDER BY p.code
                        """).setParameter("pid", planId).getResultList();

        assertThat(rows).hasSize(3);
        // household_homeless=true, has_existing_jeonse_loan=false → 두 조건 다 충족 → PASS.
        assertThat(rows).allSatisfy(row -> assertThat(row[1]).isEqualTo("PASS"));

        long basisCount =
                ((Number) em.createNativeQuery("""
                        SELECT count(*) FROM verdict_basis b
                        JOIN policy_verdict v ON v.id = b.verdict_id
                        WHERE v.plan_id = :pid
                        """).setParameter("pid", planId).getSingleResult()).longValue();
        assertThat(basisCount).isEqualTo(6L); // 정책 3개 × 조건 2개

        // 재판정해도 (plan_id, policy_id, rule_id) 유니크 제약에 안 걸리고 같은 행을 덮어써야 한다.
        service.evaluate(memberId, planId);
        em.flush();
        em.clear();

        long verdictCount = ((Number) em.createNativeQuery("SELECT count(*) FROM policy_verdict WHERE plan_id = :pid")
                        .setParameter("pid", planId)
                        .getSingleResult())
                .longValue();
        assertThat(verdictCount).isEqualTo(3L);
    }

    @Test
    void should_persistPropertyIdAndPass_when_evaluatingReturnGuaranteeAgainstRealPostgres() {
        service.evaluateReturnGuarantees(memberId, planId, propertyId);
        em.flush();
        em.clear();

        @SuppressWarnings("unchecked")
        List<Object[]> rows =
                em.createNativeQuery("""
                        SELECT p.code, v.verdict, v.property_id
                        FROM policy_verdict v JOIN policy p ON p.id = v.policy_id
                        WHERE v.plan_id = :pid AND p.code LIKE 'RETURN-GUARANTEE-%'
                        ORDER BY p.code
                        """).setParameter("pid", planId).getResultList();

        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row[1]).isEqualTo("PASS");
            assertThat(((Number) row[2]).longValue()).isEqualTo(propertyId);
        });
    }

    @Test
    void should_persistRejectionReasonWithAlternative_when_returnGuaranteeFailsPriceRatio() {
        // 공시가 5억 × 1.26 = 6억 3천 < 보증금 7억 → 126% 룰 위반 → FAIL.
        Long badPropertyId = ((Number) em.createNativeQuery(
                                "INSERT INTO property (plan_id, deposit, official_price) VALUES (:pid, 700000000, 500000000) RETURNING id")
                        .setParameter("pid", planId)
                        .getSingleResult())
                .longValue();

        service.evaluateReturnGuarantees(memberId, planId, badPropertyId);
        em.flush();
        em.clear();

        Long hugPolicyId = ((Number) em.createNativeQuery("SELECT id FROM policy WHERE code = 'RETURN-GUARANTEE-HUG'")
                        .getSingleResult())
                .longValue();
        Long sgiPolicyId = ((Number) em.createNativeQuery("SELECT id FROM policy WHERE code = 'RETURN-GUARANTEE-SGI'")
                        .getSingleResult())
                .longValue();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT r.reason_code, r.alternative_id
                        FROM rejection_reason r JOIN policy_verdict v ON v.id = r.verdict_id
                        WHERE v.plan_id = :pid AND v.policy_id = :policyId
                        """)
                .setParameter("pid", planId)
                .setParameter("policyId", hugPolicyId)
                .getResultList();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo("PRICE_RATIO_126");
        assertThat(((Number) rows.get(0)[1]).longValue()).isEqualTo(sgiPolicyId);
    }

    @Test
    void should_persistExpectedAmountAndRate_when_youthPolicyHasAmountSpec() {
        // 최소 조건 + amount/rate 스펙까지 포함한 ACTIVE 버전(version 3)을 새로 만든다 —
        // setUp() 이 이미 넣은 version 2(조건만)보다 최신이라 이게 선택된다.
        em.createNativeQuery("""
                        INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, reviewed_by)
                        SELECT id, 3,
                            '{"operator":"AND","conditions":[
                                {"code":"HOUSEHOLD_HOMELESS","field":"household_homeless","op":"eq","value":true}
                            ],
                            "amount":{"ratio_fact_code":"FCT-008","cap_fact_code":"FCT-171"},
                            "rate":{"min_fact_code":"FCT-172","max_fact_code":"FCT-173"}}'::jsonb,
                            'ACTIVE', '2026-09-04', 'integration-test'
                        FROM policy WHERE code = 'JEONSE-YOUTH-BEOTIMMOK'
                        """).executeUpdate();
        em.createNativeQuery("UPDATE plan_input SET hope_deposit = 200000000 WHERE plan_id = :pid")
                .setParameter("pid", planId)
                .executeUpdate();

        service.evaluate(memberId, planId);
        em.flush();
        em.clear();

        // 시드값을 직접 읽어서 같은 공식으로 재계산 — 판정 결과끼리 비교하지 않는다.
        double ratio = ((Number)
                        em.createNativeQuery("SELECT value_num FROM config_effective WHERE fact_code = 'FCT-008'")
                                .getSingleResult())
                .doubleValue();
        long cap = ((Number) em.createNativeQuery("SELECT value_num FROM config_effective WHERE fact_code = 'FCT-171'")
                        .getSingleResult())
                .longValue();
        long expectedLoan = Math.min((long) (200_000_000L * ratio / 100), cap);

        Object[] row =
                (Object[]) em.createNativeQuery("""
                        SELECT v.expected_amount, v.expected_rate
                        FROM policy_verdict v JOIN policy p ON p.id = v.policy_id
                        WHERE v.plan_id = :pid AND p.code = 'JEONSE-YOUTH-BEOTIMMOK'
                        """).setParameter("pid", planId).getSingleResult();

        assertThat(((Number) row[0]).longValue()).isEqualTo(expectedLoan);
        assertThat(row[1]).isNotNull();
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

    private void activateSimpleReviewedRule(String policyCode) {
        em.createNativeQuery("""
                        INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, reviewed_by)
                        SELECT id, 2,
                            '{"operator":"AND","conditions":[
                                {"code":"HOUSEHOLD_HOMELESS","field":"household_homeless","op":"eq","value":true},
                                {"code":"NO_DUPLICATE_LOAN","field":"has_existing_jeonse_loan","op":"eq","value":false}
                            ]}'::jsonb,
                            'ACTIVE', '2026-09-04', 'integration-test'
                        FROM policy WHERE code = :code
                        """).setParameter("code", policyCode).executeUpdate();
    }
}
