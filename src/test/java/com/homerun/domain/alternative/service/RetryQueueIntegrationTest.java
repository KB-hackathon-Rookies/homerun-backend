package com.homerun.domain.alternative.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.alternative.dto.response.RetryQueueResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * V27(#116)이 심은 실제 YOUTH-FUTURE-SAVINGS(FCT-179 연령 하한 19세)를 대상으로 한다 — V31(#140)이
 * 이 정책 version 1을 이미 ACTIVE로 승격해 뒀다. mock이 아니라 진짜 시드·진짜 fact로 재도전 큐
 * 전체 경로(레포지토리 조회 → 엔진 계산 → 응답 조립)가 실제로 이어지는지 확인한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class RetryQueueIntegrationTest {

    private final RetryQueueService service;
    private final EntityManager em;

    private Long memberId;
    private Long planId;

    RetryQueueIntegrationTest(@Autowired RetryQueueService service, @Autowired EntityManager em) {
        this.service = service;
        this.em = em;
    }

    @Test
    void should_queueYouthSavings_when_stillTooYoungAgainstRealPostgres() {
        setUpPlan("2010-01-01"); // 만 19세는 2029-01-01부터 — 지금은 확실히 미달.

        RetryQueueResponse response = service.build(memberId, planId);

        var item = response.items().stream()
                .filter(i -> i.policyCode().equals("YOUTH-FUTURE-SAVINGS"))
                .findFirst()
                .orElseThrow();
        // FCT-179(V27 시드)는 source_url 컬럼 자체가 없어 null이다 — 있는 그대로 통과시키는지만 본다.
        assertThat(item.eligibleFrom()).isEqualTo(java.time.LocalDate.of(2029, 1, 1));
        assertThat(item.daysRemaining()).isPositive();
        assertThat(item.conditionLabel()).isEqualTo("청년미래적금 연령 하한");
    }

    @Test
    void should_excludeYouthSavings_when_alreadyPastMinAgeAgainstRealPostgres() {
        setUpPlan("1995-01-01"); // 이미 19세를 한참 넘겼다 — 재도전 큐에 낄 이유가 없다.

        RetryQueueResponse response = service.build(memberId, planId);

        assertThat(response.items()).noneMatch(i -> i.policyCode().equals("YOUTH-FUTURE-SAVINGS"));
    }

    private void setUpPlan(String birthDate) {
        memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, nickname)
                        VALUES ('KAKAO', 'retry-queue-test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        planId = ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
        em.createNativeQuery(
                        "INSERT INTO plan_input (plan_id, birth_date, financial_data_confirmed) VALUES (:pid, :bd, true)")
                .setParameter("pid", planId)
                .setParameter("bd", java.time.LocalDate.parse(birthDate))
                .executeUpdate();
    }
}
