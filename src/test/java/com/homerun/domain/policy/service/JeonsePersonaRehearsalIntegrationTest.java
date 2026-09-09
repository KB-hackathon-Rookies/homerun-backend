package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.openbanking.type.Persona;
import com.homerun.domain.plan.service.OpenBankingPlanSyncService;
import com.homerun.domain.policy.dto.response.JeonsePolicyVerdictListResponse;
import com.homerun.domain.policy.dto.response.PolicyVerdictResponse;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모 리허설. 가짜 오픈뱅킹 연동(mockConnect)으로 페르소나 자산을 계획 입력에 적재한 뒤
 * 실제 전세 판정을 돌려, 소득 컷오프에서 판정이 갈리는지 진짜 Postgres 에서 확인한다.
 *
 * <p>청년 버팀목 소득 기준(FCT-003 연 5,000만) 기준으로 박사회(연 5,040만)만 FAIL 이고
 * 김첫집·이빠듯은 FAIL 이 아니어야 한다. 세 페르소나는 보증금·순자산에선 안 걸리므로 소득이
 * 유일한 차이다. mockConnect 가 plan_input 까지 확정 상태로 채우지 않으면 전부 NEED_INFO 가 되어
 * 이 테스트가 깨진다 — 그게 연결이 실제로 됐는지 잡는 지점이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JeonsePersonaRehearsalIntegrationTest {

    private final OpenBankingPlanSyncService syncService;
    private final JeonsePolicyVerdictService verdictService;
    private final EntityManager em;

    private Long memberId;
    private Long planId;

    JeonsePersonaRehearsalIntegrationTest(
            @Autowired OpenBankingPlanSyncService syncService,
            @Autowired JeonsePolicyVerdictService verdictService,
            @Autowired EntityManager em) {
        this.syncService = syncService;
        this.verdictService = verdictService;
        this.em = em;
    }

    @BeforeEach
    void setUp() {
        memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, name)
                        VALUES ('KAKAO', 'persona-test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        planId = ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
        // 소득 외 조건은 세 페르소나 모두 통과하도록 고정한다 — 소득만 차이로 남긴다.
        em.createNativeQuery("""
                        INSERT INTO plan_input
                            (plan_id, household_homeless, householder_status, has_existing_jeonse_loan, hope_deposit)
                        VALUES (:pid, true, 'CURRENT', false, 150000000)
                        """).setParameter("pid", planId).executeUpdate();

        // 실 시드보다 높은 버전의 ACTIVE 룰로 청년 버팀목에 소득·순자산·보증금 조건을 심는다.
        activateYouthFullRule();
        activateSimpleRule("JEONSE-GENERAL-BEOTIMMOK");
        activateSimpleRule("JEONSE-SEOUL-INTEREST-SUPPORT");
    }

    @Test
    void should_failOnlyHighIncomePersona_whenEvaluatingYouthLoanAfterMockConnect() {
        assertThat(youthVerdictAfterMockConnect(Persona.PARK_SENIOR)).isEqualTo(PolicyVerdictResult.FAIL);
        assertThat(youthVerdictAfterMockConnect(Persona.KIM_FIRST)).isNotEqualTo(PolicyVerdictResult.FAIL);
        assertThat(youthVerdictAfterMockConnect(Persona.LEE_TIGHT)).isNotEqualTo(PolicyVerdictResult.FAIL);
    }

    private PolicyVerdictResult youthVerdictAfterMockConnect(Persona persona) {
        syncService.mockConnect(memberId, planId, persona);
        em.flush();
        em.clear();

        JeonsePolicyVerdictListResponse result = verdictService.evaluate(memberId, planId);
        return result.results().stream()
                .filter(r -> r.policyCode().equals("JEONSE-YOUTH-BEOTIMMOK"))
                .map(PolicyVerdictResponse::verdict)
                .findFirst()
                .orElseThrow();
    }

    private void activateYouthFullRule() {
        em.createNativeQuery("""
                        INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, reviewed_by)
                        SELECT id, 30,
                            '{"operator":"AND","conditions":[
                                {"code":"HOUSEHOLD_HOMELESS","field":"household_homeless","op":"eq","value":true},
                                {"code":"NO_DUPLICATE_LOAN","field":"has_existing_jeonse_loan","op":"eq","value":false},
                                {"code":"INCOME_CAP","field":"monthly_income","fact_code":"FCT-003","op":"annual_lte"},
                                {"code":"NET_ASSET_CAP","field":"net_assets","fact_code":"FCT-170","op":"lte"},
                                {"code":"DEPOSIT_CAP","field":"hope_deposit","fact_code":"FCT-175","op":"lte"}
                            ]}'::jsonb,
                            'ACTIVE', '2026-09-04', 'persona-rehearsal'
                        FROM policy WHERE code = 'JEONSE-YOUTH-BEOTIMMOK'
                        """).executeUpdate();
    }

    private void activateSimpleRule(String policyCode) {
        em.createNativeQuery("""
                        INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, reviewed_by)
                        SELECT id, 30,
                            '{"operator":"AND","conditions":[
                                {"code":"HOUSEHOLD_HOMELESS","field":"household_homeless","op":"eq","value":true}
                            ]}'::jsonb,
                            'ACTIVE', '2026-09-04', 'persona-rehearsal'
                        FROM policy WHERE code = :code
                        """).setParameter("code", policyCode).executeUpdate();
    }
}
