package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.contract.entity.LeaseEnd;
import com.homerun.domain.contract.repository.LeaseEndRepository;
import com.homerun.domain.contract.type.LeaseDecision;
import com.homerun.domain.contract.type.RenewalMethod;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** lease_end 가 실제 Postgres 에서 왕복하는지와 enum↔CHECK 짝을 본다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class LeaseEndIntegrationTest {

    private final LeaseEndRepository leaseEnds;
    private final EntityManager em;

    LeaseEndIntegrationTest(@Autowired LeaseEndRepository leaseEnds, @Autowired EntityManager em) {
        this.leaseEnds = leaseEnds;
        this.em = em;
    }

    @Test
    void should_roundTripEnums_againstRealPostgres() {
        Long planId = setUpPlan();
        LeaseEnd e = new LeaseEnd(planId);
        e.decide(LeaseDecision.RENEW, RenewalMethod.CLAIM, LocalDate.of(2026, 3, 1), Instant.now());
        leaseEnds.save(e);
        em.flush();
        em.clear();

        LeaseEnd reloaded = leaseEnds.findByPlanId(planId).orElseThrow();
        assertThat(reloaded.getDecision()).isEqualTo(LeaseDecision.RENEW);
        assertThat(reloaded.getRenewalMethod()).isEqualTo(RenewalMethod.CLAIM);
        assertThat(reloaded.isClaimRightUsed()).isTrue();
    }

    @Test
    void should_rejectBadDecision_becauseEnumAndCheckArePaired() {
        Long planId = setUpPlan();
        assertThatThrownBy(() -> {
                    em.createNativeQuery("INSERT INTO lease_end (plan_id, decision) VALUES (:p, 'BOGUS')")
                            .setParameter("p", planId)
                            .executeUpdate();
                    em.flush();
                })
                .isInstanceOf(Exception.class);
    }

    private Long setUpPlan() {
        Long memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, name)
                        VALUES ('KAKAO', 'le-test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        return ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
    }
}
