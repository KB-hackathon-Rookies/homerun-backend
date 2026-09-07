package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.settlement.entity.ReturnGuaranteeEnrollment;
import com.homerun.domain.settlement.repository.ReturnGuaranteeEnrollmentRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** return_guarantee 가 실제 Postgres 에서 왕복하는지와 계획당 1건 UNIQUE 를 본다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ReturnGuaranteeEnrollmentIntegrationTest {

    private final ReturnGuaranteeEnrollmentRepository enrollments;
    private final EntityManager em;

    ReturnGuaranteeEnrollmentIntegrationTest(
            @Autowired ReturnGuaranteeEnrollmentRepository enrollments, @Autowired EntityManager em) {
        this.enrollments = enrollments;
        this.em = em;
    }

    @Test
    void should_roundTrip() {
        Long planId = setUpPlan();
        ReturnGuaranteeEnrollment e = new ReturnGuaranteeEnrollment(planId);
        e.update(true, true, LocalDate.of(2026, 5, 1));
        enrollments.save(e);
        em.flush();
        em.clear();

        ReturnGuaranteeEnrollment reloaded = enrollments.findByPlanId(planId).orElseThrow();
        assertThat(reloaded.isEnrolled()).isTrue();
        assertThat(reloaded.isFeePaid()).isTrue();
        assertThat(reloaded.getEnrolledAt()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void should_enforceOnePerPlan() {
        Long planId = setUpPlan();
        enrollments.saveAndFlush(new ReturnGuaranteeEnrollment(planId));

        assertThatThrownBy(() -> enrollments.saveAndFlush(new ReturnGuaranteeEnrollment(planId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long setUpPlan() {
        Long memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, name)
                        VALUES ('KAKAO', 'rg-test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        return ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
    }
}
