package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.service.PlanInputService;
import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.policy.dto.response.PreferentialRateChangeResponse;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * plan_input_history 의 snapshot 은 제네릭 {@code Map<String,Object>}(JSONB)이라, 실제
 * Postgres 를 왕복하면 company_size 가 CompanySize 가 아니라 문자열로 돌아온다 — mock으로는
 * 이 캐스팅 문제를 못 잡는다. {@link PlanInputService#save} 를 실제로 두 번 호출해서 진짜
 * 이력 캡처 경로를 그대로 태운다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PreferentialRateChangeIntegrationTest {

    private final PlanInputService planInputService;
    private final PreferentialRateChangeService service;
    private final EntityManager em;

    private Long memberId;
    private Long planId;

    PreferentialRateChangeIntegrationTest(
            @Autowired PlanInputService planInputService,
            @Autowired PreferentialRateChangeService service,
            @Autowired EntityManager em) {
        this.planInputService = planInputService;
        this.service = service;
        this.em = em;
    }

    @BeforeEach
    void setUp() {
        memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, nickname)
                        VALUES ('KAKAO', 'rate-change-test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        planId = ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
    }

    @Test
    void should_detectPromotion_when_companySizeChangesAgainstRealPostgres() {
        planInputService.save(memberId, planId, companyRequest(CompanySize.LARGE));
        em.flush();
        em.clear();
        planInputService.save(memberId, planId, companyRequest(CompanySize.SMALL));
        em.flush();
        em.clear();

        PreferentialRateChangeResponse response = service.detect(memberId, planId);

        assertThat(response.changes()).hasSize(1);
        assertThat(response.changes().get(0).code()).isEqualTo("YOUNG_EMPLOYMENT_DISCOUNT");
        // 시드값을 직접 읽어서 비교한다 — 응답끼리 비교하지 않는다.
        java.math.BigDecimal seededRate = (java.math.BigDecimal)
                em.createNativeQuery("SELECT value_num FROM config_effective WHERE fact_code = 'FCT-019'")
                        .getSingleResult();
        assertThat(response.changes().get(0).rateBonus()).isEqualByComparingTo(seededRate);
    }

    @Test
    void should_returnEmpty_when_companySizeUnchangedAgainstRealPostgres() {
        planInputService.save(memberId, planId, companyRequest(CompanySize.SMALL));
        em.flush();
        em.clear();
        planInputService.save(memberId, planId, companyRequest(CompanySize.STARTUP));
        em.flush();
        em.clear();

        PreferentialRateChangeResponse response = service.detect(memberId, planId);

        assertThat(response.changes()).isEmpty();
    }

    private PlanInputRequest companyRequest(CompanySize companySize) {
        return new PlanInputRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                companySize,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                Set.of());
    }
}
