package com.homerun.domain.property.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import com.homerun.domain.property.dto.response.PropertyPolicyVerdictListResponse;
import com.homerun.domain.property.dto.response.PropertyPolicyVerdictResponse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매물×상품 판정(DR-10)을 실제 Postgres 에서 확인한다.
 *
 * <p>핵심은 <b>매물마다 결과가 갈리는 것</b>이다. 지금까지는 {@code policy_verdict} 의
 * {@code UNIQUE (plan_id, policy_id, rule_id)} 때문에 매물을 여러 개 판정해도 마지막 하나만
 * 남았다. 그래서 면적만 다른 매물 둘을 넣고 둘 다 제 결과를 유지하는지 본다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PropertyPolicyVerdictIntegrationTest {

    private static final String YOUTH = "JEONSE-YOUTH-BEOTIMMOK";

    private final PropertyPolicyVerdictService service;
    private final EntityManager em;

    private Long memberId;
    private Long planId;

    PropertyPolicyVerdictIntegrationTest(@Autowired PropertyPolicyVerdictService service, @Autowired EntityManager em) {
        this.service = service;
        this.em = em;
    }

    @BeforeEach
    void setUp() {
        memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, nickname)
                        VALUES ('KAKAO', 'ppv-' || nextval('app_user_id_seq'), 'tester')
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
                             hope_deposit, birth_date, monthly_income, net_assets, financial_data_confirmed)
                        VALUES (:pid, true, 'CURRENT', false, 180000000, '1998-05-14', 2450000, 36000000, true)
                        """).setParameter("pid", planId).executeUpdate();
    }

    @Test
    void should_keepBothPropertyVerdicts_when_twoPropertiesDifferOnlyByArea() {
        // 청년 버팀목 ACTIVE 규칙(V25 version 3)에 AREA_CAP(FCT-005 = 85㎡)이 들어 있다.
        Long small = property(new BigDecimal("42.35"));
        Long large = property(new BigDecimal("120.00"));

        service.evaluate(memberId, planId, small);
        service.evaluate(memberId, planId, large);
        em.flush();
        em.clear();

        // 매물 둘의 판정이 모두 남아야 한다. 이게 policy_verdict 로는 안 되던 것이다.
        assertThat(youthOf(service.get(memberId, planId, large)).failCodes()).contains("AREA_CAP");
        assertThat(youthOf(service.get(memberId, planId, small)).failCodes()).doesNotContain("AREA_CAP");

        long rows = ((Number) em.createNativeQuery("""
                        SELECT count(*) FROM property_policy_verdict
                        WHERE property_id IN (:a, :b)
                        """)
                        .setParameter("a", small)
                        .setParameter("b", large)
                        .getSingleResult())
                .longValue();
        assertThat(rows).isEqualTo(6); // 매물 2개 × 상품 3개
    }

    @Test
    void should_reportFailStepTwo_when_areaExceedsCap() {
        Long large = property(new BigDecimal("120.00"));

        PropertyPolicyVerdictResponse youth = youthOf(service.evaluate(memberId, planId, large));

        assertThat(youth.status()).isEqualTo(PolicyVerdictResult.FAIL);
        // 면적은 명세서 2-1 의 STEP 2(건축물대장 자동조회)에서 걸린다.
        assertThat(youth.failStep()).isEqualTo((short) 2);
    }

    @Test
    void should_collectEveryFailCode_notJustTheFirst() {
        // BR-12. 탈락해도 전체 조건을 평가해 코드를 배열로 모은다 — 무엇을 몇 개 고쳐야 하는지
        // 알아야 하기 때문이다. 면적 초과 + 위반건축물을 동시에 준다.
        Long bad = property(new BigDecimal("120.00"));
        em.createNativeQuery("UPDATE property SET is_violation_building = true WHERE id = :id")
                .setParameter("id", bad)
                .executeUpdate();
        em.flush();
        em.clear();

        PropertyPolicyVerdictResponse youth = youthOf(service.evaluate(memberId, planId, bad));

        assertThat(youth.failCodes()).contains("AREA_CAP", "NOT_VIOLATION_BUILDING");
        // 둘 다 걸리면 더 앞 단계를 알려준다.
        assertThat(youth.failStep()).isEqualTo((short) 2);
    }

    @Test
    void should_overwriteOnReevaluation_becauseItHoldsCurrentStateNotHistory() {
        Long id = property(new BigDecimal("120.00"));
        service.evaluate(memberId, planId, id);
        em.flush();

        em.createNativeQuery("UPDATE property SET exclusive_area = 42.35 WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();
        em.flush();
        em.clear();
        service.evaluate(memberId, planId, id);
        em.flush();
        em.clear();

        long rows = ((Number)
                        em.createNativeQuery("SELECT count(*) FROM property_policy_verdict WHERE property_id = :id")
                                .setParameter("id", id)
                                .getSingleResult())
                .longValue();
        assertThat(rows).isEqualTo(3); // 상품 3개. 재판정해도 행이 늘지 않는다
        assertThat(youthOf(service.get(memberId, planId, id)).failCodes()).doesNotContain("AREA_CAP");
    }

    private PropertyPolicyVerdictResponse youthOf(PropertyPolicyVerdictListResponse response) {
        return response.results().stream()
                .filter(result -> YOUTH.equals(result.policyCode()))
                .findFirst()
                .orElseThrow();
    }

    /** 면적만 다른 매물을 만든다. 나머지 조건은 통과하도록 채운다. */
    private Long property(BigDecimal area) {
        return ((Number) em.createNativeQuery("""
                        INSERT INTO property
                            (plan_id, deposit, exclusive_area, area_source,
                             is_violation_building, is_multi_household)
                        VALUES (:pid, 180000000, :area, 'AUTO', false, false)
                        RETURNING id
                        """)
                        .setParameter("pid", planId)
                        .setParameter("area", area)
                        .getSingleResult())
                .longValue();
    }
}
