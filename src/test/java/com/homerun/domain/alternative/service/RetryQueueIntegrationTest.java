package com.homerun.domain.alternative.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.alternative.dto.response.RetryQueueResponse;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * V36 이 심은 청년 주거급여 분리지급 규칙(FCT-209 연령 하한 19세)을 대상으로 한다. mock 이 아니라
 * 진짜 시드·진짜 fact 로 재도전 큐 전체 경로(레포지토리 조회 → 엔진 계산 → 응답 조립)가 실제로
 * 이어지는지 확인한다.
 *
 * <p>원래는 청년미래적금으로 검증했는데 그 정책이 범위에서 빠지면서(#175) ACTIVE 규칙 중 나이
 * 하한을 쓰는 것이 없어졌다. 분리지급 규칙은 사람 검수 전이라 DRAFT 이므로, 이 테스트 안에서만
 * ACTIVE 사본을 만들어 쓴다 — 트랜잭션 롤백으로 정리된다.
 *
 * <p>사본은 rule_json 을 새로 적지 않고 <b>시드된 것을 그대로 복사</b>한다. 조건식을 다시 적으면
 * 시드가 바뀌어도 테스트는 옛 조건을 계속 통과시킨다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class RetryQueueIntegrationTest {

    private static final String POLICY_CODE = "HOUSING-BENEFIT-YOUTH-SPLIT";

    private final RetryQueueService service;
    private final EntityManager em;

    private Long memberId;
    private Long planId;

    RetryQueueIntegrationTest(@Autowired RetryQueueService service, @Autowired EntityManager em) {
        this.service = service;
        this.em = em;
    }

    @Test
    void should_queueYouthHousingBenefit_when_stillTooYoungAgainstRealPostgres() {
        setUpPlan("2010-01-01"); // 만 19세는 2029-01-01부터 — 지금은 확실히 미달.
        activateSeededRule();

        RetryQueueResponse response = service.build(memberId, planId);

        var item = response.items().stream()
                .filter(i -> i.policyCode().equals(POLICY_CODE))
                .findFirst()
                .orElseThrow();
        assertThat(item.eligibleFrom()).isEqualTo(LocalDate.of(2029, 1, 1));
        assertThat(item.daysRemaining()).isPositive();
        assertThat(item.conditionLabel()).isEqualTo("청년 분리지급 연령 하한");
    }

    @Test
    void should_excludeYouthHousingBenefit_when_alreadyPastMinAgeAgainstRealPostgres() {
        setUpPlan("1995-01-01"); // 이미 19세를 한참 넘겼다 — 재도전 큐에 낄 이유가 없다.
        activateSeededRule();

        RetryQueueResponse response = service.build(memberId, planId);

        assertThat(response.items()).noneMatch(i -> i.policyCode().equals(POLICY_CODE));
    }

    @Test
    void should_beEmpty_when_noRuleIsActive() {
        // 규칙을 승격하지 않으면 훑을 대상이 없다. 청년미래적금을 뺀 뒤의 실제 시드 상태다(#175).
        setUpPlan("2010-01-01");

        assertThat(service.build(memberId, planId).items()).isEmpty();
    }

    /** 시드된 DRAFT 규칙을 그대로 복사해 ACTIVE 사본(version 2)을 만든다. */
    private void activateSeededRule() {
        em.createNativeQuery("""
                        INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, reviewed_by)
                        SELECT policy_id, 2, rule_json, 'ACTIVE', DATE '2026-09-06', 'retry-queue-test'
                        FROM policy_rule
                        WHERE policy_id = (SELECT id FROM policy WHERE code = :code) AND version = 1
                        """).setParameter("code", POLICY_CODE).executeUpdate();
        em.flush();
        em.clear();
    }

    private void setUpPlan(String birthDate) {
        memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, name)
                        VALUES ('KAKAO', 'retry-queue-test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        planId = ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'WOLSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
        em.createNativeQuery(
                        "INSERT INTO plan_input (plan_id, birth_date, financial_data_confirmed) VALUES (:pid, :bd, true)")
                .setParameter("pid", planId)
                .setParameter("bd", LocalDate.parse(birthDate))
                .executeUpdate();
    }
}
