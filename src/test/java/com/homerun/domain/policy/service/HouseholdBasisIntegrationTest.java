package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.policy.dto.response.JeonsePolicyVerdictListResponse;
import com.homerun.domain.policy.type.HouseholdBasis;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * V35 가 심은 가구 기준 축을 실제 Postgres 에서 확인한다(POL-01-03 Phase 0).
 *
 * <p>가장 중요한 것은 <b>판정이 안 바뀌었다</b>는 것이다. 축은 근거에 붙는 표시일 뿐이라
 * 기존 조건은 전부 SELF 로 남고 verdict 도 그대로여야 한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class HouseholdBasisIntegrationTest {

    private final JeonsePolicyVerdictService service;
    private final EntityManager em;

    private Long memberId;
    private Long planId;

    HouseholdBasisIntegrationTest(@Autowired JeonsePolicyVerdictService service, @Autowired EntityManager em) {
        this.service = service;
        this.em = em;
    }

    @Test
    void should_markYouthSavingsHouseholdIncomeAsOrigin_inTheSeededRule() {
        // V35 가 조건 배열의 순서를 지키면서 그 조건에만 키를 더했는지 시드에서 직접 읽는다.
        @SuppressWarnings("unchecked")
        List<Object[]> conditions = em.createNativeQuery("""
                        SELECT element->>'code', element->>'household_basis'
                        FROM policy_rule pr,
                             jsonb_array_elements(pr.rule_json->'conditions') WITH ORDINALITY AS t(element, ord)
                        WHERE pr.policy_id = (SELECT id FROM policy WHERE code = 'YOUTH-FUTURE-SAVINGS')
                          AND pr.version = 1
                        ORDER BY ord
                        """).getResultList();

        assertThat(conditions)
                .extracting(row -> row[0])
                .containsExactly("AGE_RANGE", "INCOME_CAP_BY_EMPLOYMENT", "HOUSEHOLD_INCOME_RATIO", "EXCLUSION_CHECK");
        assertThat(conditions).extracting(row -> row[1]).containsExactly(null, null, "ORIGIN", null);
    }

    @Test
    void should_keepExistingConditionsOnSelf_andPersistItAgainstRealPostgres() {
        setUpJeonsePlan();
        activateSimpleRule("JEONSE-YOUTH-BEOTIMMOK");

        JeonsePolicyVerdictListResponse response = service.evaluate(memberId, planId);

        var youth = response.results().stream()
                .filter(result -> result.policyCode().equals("JEONSE-YOUTH-BEOTIMMOK"))
                .findFirst()
                .orElseThrow();

        // 축을 안 밝힌 기존 조건은 독립가구로 본다 — 판정도 그대로 PASS 여야 한다.
        assertThat(youth.verdict()).isEqualTo(PolicyVerdictResult.PASS);
        assertThat(youth.basis())
                .allSatisfy(basis -> assertThat(basis.householdBasis()).isEqualTo(HouseholdBasis.SELF));
        assertThat(youth.basis().get(0).householdBasisLabel()).isEqualTo("독립가구");

        em.flush();
        em.clear();

        // 응답에만 있고 저장이 안 되면 나중에 근거를 다시 볼 때 축이 사라진다.
        @SuppressWarnings("unchecked")
        List<String> stored =
                em.createNativeQuery("""
                        SELECT b.household_basis FROM verdict_basis b
                        JOIN policy_verdict v ON v.id = b.verdict_id
                        WHERE v.plan_id = :pid
                        """).setParameter("pid", planId).getResultList();
        assertThat(stored).isNotEmpty().allSatisfy(basis -> assertThat(basis).isEqualTo("SELF"));
    }

    @Test
    void should_rejectUnknownHouseholdBasis_becauseEnumAndCheckConstraintArePaired() {
        setUpJeonsePlan();
        Long verdictId =
                ((Number) em.createNativeQuery("""
                        INSERT INTO policy_verdict (plan_id, policy_id, verdict, engine_version)
                        SELECT :pid, id, 'PASS', 'test' FROM policy WHERE code = 'JEONSE-YOUTH-BEOTIMMOK'
                        RETURNING id
                        """).setParameter("pid", planId).getSingleResult()).longValue();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
                    em.createNativeQuery("""
                            INSERT INTO verdict_basis (verdict_id, condition_code, condition_label, household_basis)
                            VALUES (:vid, 'X', '라벨', 'PARENT')
                            """).setParameter("vid", verdictId).executeUpdate();
                    em.flush();
                })
                .hasMessageContaining("ck_verdict_basis_household");
    }

    private void setUpJeonsePlan() {
        memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, name)
                        VALUES ('KAKAO', 'household-basis-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        planId = ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
        em.createNativeQuery("""
                        INSERT INTO plan_input (plan_id, household_homeless, financial_data_confirmed)
                        VALUES (:pid, true, true)
                        """).setParameter("pid", planId).executeUpdate();
    }

    /** 조건 하나짜리 ACTIVE 규칙. 버전 번호는 기존 시드·다른 통합테스트와 겹치지 않게 고른다. */
    private void activateSimpleRule(String policyCode) {
        em.createNativeQuery("""
                        INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, reviewed_by)
                        SELECT id, 21,
                            '{"operator":"AND","conditions":[
                                {"code":"HOUSEHOLD_HOMELESS","field":"household_homeless","op":"eq","value":true}
                            ]}'::jsonb,
                            'ACTIVE', '2026-09-05', 'household-basis-test'
                        FROM policy WHERE code = :code
                        """).setParameter("code", policyCode).executeUpdate();
    }
}
