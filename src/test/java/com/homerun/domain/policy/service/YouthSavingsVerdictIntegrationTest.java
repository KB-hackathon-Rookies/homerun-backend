package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.policy.dto.response.ConditionBasisResponse;
import com.homerun.domain.policy.dto.response.PolicyVerdictResponse;
import com.homerun.domain.policy.entity.PolicyRule;
import com.homerun.domain.policy.model.RuleCondition;
import com.homerun.domain.policy.repository.PolicyRuleRepository;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * V27(#116)이 심은 실제 YOUTH-FUTURE-SAVINGS 시드를 대상으로 한다. version 1은 V31(#140)이
 * 이미 ACTIVE로 승격했고, 조건을 더 추가한 버전은 트랜잭션 안에서만 따로 만든다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class YouthSavingsVerdictIntegrationTest {

    private final YouthSavingsVerdictService service;
    private final PolicyRuleRepository policyRuleRepository;
    private final EntityManager em;

    private Long memberId;
    private Long planId;
    private Long policyId;

    YouthSavingsVerdictIntegrationTest(
            @Autowired YouthSavingsVerdictService service,
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
                        VALUES ('KAKAO', 'youth-savings-test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        // 월세 계획으로 만든다 — 이 정책이 lease_type 과 무관하게 도는지가 핵심 검증 포인트다.
        planId = ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'WOLSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
        em.createNativeQuery("INSERT INTO plan_input (plan_id, financial_data_confirmed) VALUES (:pid, true)")
                .setParameter("pid", planId)
                .executeUpdate();

        policyId = ((Number) em.createNativeQuery("SELECT id FROM policy WHERE code = 'YOUTH-FUTURE-SAVINGS'")
                        .getSingleResult())
                .longValue();
    }

    @Test
    void should_deserializeSeededActiveRuleJson_when_readFromRealPostgres() {
        Long activeRuleId = ((Number) em.createNativeQuery(
                                "SELECT id FROM policy_rule WHERE policy_id = :pid AND status = 'ACTIVE' AND version = 1")
                        .setParameter("pid", policyId)
                        .getSingleResult())
                .longValue();

        PolicyRule active = policyRuleRepository.findById(activeRuleId).orElseThrow();

        assertThat(active.getRuleJson().conditions())
                .extracting(RuleCondition::code)
                .containsExactly("AGE_RANGE", "INCOME_CAP_BY_EMPLOYMENT", "HOUSEHOLD_INCOME_RATIO", "EXCLUSION_CHECK");
    }

    @Test
    void should_alwaysReturnNeedInfo_when_evaluatingAgainstRealPostgresEvenWithGoodInputs() {
        // 나이·소득이 완벽해도 가구소득·제외사유를 확인 못 해서 전체는 NEED_INFO 여야 한다 —
        // 이게 이 정책의 의도된 설계다.
        activateAgeAndIncomeOnlyRule();
        em.createNativeQuery("""
                        UPDATE plan_input SET birth_date = '2000-09-04', employment_type = 'FULL_TIME',
                            monthly_income = 3000000 WHERE plan_id = :pid
                        """).setParameter("pid", planId).executeUpdate();

        PolicyVerdictResponse response = service.evaluate(memberId, planId);

        assertThat(response.verdict()).isEqualTo(PolicyVerdictResult.NEED_INFO);
        ConditionBasisResponse ageBasis = response.basis().stream()
                .filter(b -> b.code().equals("AGE_RANGE"))
                .findFirst()
                .orElseThrow();
        assertThat(ageBasis.isMet()).isTrue();
    }

    @Test
    void should_failAgeCondition_when_tooOldAgainstRealPostgres() {
        activateAgeAndIncomeOnlyRule();
        em.createNativeQuery("""
                        UPDATE plan_input SET birth_date = '1985-01-01', employment_type = 'FULL_TIME',
                            monthly_income = 3000000 WHERE plan_id = :pid
                        """).setParameter("pid", planId).executeUpdate();

        PolicyVerdictResponse response = service.evaluate(memberId, planId);

        assertThat(response.verdict()).isEqualTo(PolicyVerdictResult.FAIL);
    }

    private void activateAgeAndIncomeOnlyRule() {
        // version 2: V27(#116)이 심은 DRAFT가 version 1 뿐이라 안 겹친다.
        em.createNativeQuery("""
                        INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, reviewed_by)
                        SELECT id, 2,
                            '{"operator":"AND","conditions":[
                                {"code":"AGE_RANGE","field":"birth_date","adjust_field":"military_months",
                                 "fact_code":"FCT-179","alt_fact_code":"FCT-180","op":"age_range_adjusted"},
                                {"code":"INCOME_CAP_BY_EMPLOYMENT","field":"monthly_income",
                                 "fact_code":"FCT-181","alt_fact_code":"FCT-182","op":"annual_lte_by_employment_type"},
                                {"code":"HOUSEHOLD_INCOME_RATIO","fact_code":"FCT-087","op":"external_check"}
                            ]}'::jsonb,
                            'ACTIVE', '2026-09-04', 'integration-test'
                        FROM policy WHERE code = 'YOUTH-FUTURE-SAVINGS'
                        """).executeUpdate();
    }
}
