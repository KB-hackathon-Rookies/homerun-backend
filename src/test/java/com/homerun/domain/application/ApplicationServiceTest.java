package com.homerun.domain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.application.dto.ApplicationDtos.ApplicationView;
import com.homerun.domain.application.dto.ApplicationDtos.CreateRequest;
import com.homerun.domain.application.dto.ApplicationDtos.UpdateRequest;
import com.homerun.domain.application.repository.PolicyApplicationRepository;
import com.homerun.domain.application.service.ApplicationService;
import com.homerun.domain.application.service.RejectGuidance;
import com.homerun.domain.application.type.ApplicationStatus;
import com.homerun.domain.application.type.RejectStage;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

    private final PolicyApplicationRepository applications;
    private final PlanRepository plans;
    private final EntityManager em;
    private final ApplicationService service;

    private Long ownerId;
    private Long planId;

    ApplicationServiceTest(
            @Autowired PolicyApplicationRepository applications,
            @Autowired PlanRepository plans,
            @Autowired EntityManager em) {
        this.applications = applications;
        this.plans = plans;
        this.em = em;
        this.service = new ApplicationService(applications, plans, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @BeforeEach
    void setUpPlan() {
        ownerId = (Long) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, nickname)
                        VALUES ('KAKAO', 'test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult();
        planId = (Long)
                em.createNativeQuery("INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'WOLSE') RETURNING id")
                        .setParameter("uid", ownerId)
                        .getSingleResult();
        em.flush();
        em.clear();
    }

    private ApplicationService serviceAt(Instant instant) {
        return new ApplicationService(applications, plans, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private Long newPolicy() {
        return (Long) em.createNativeQuery("""
                        INSERT INTO policy (code, name, category, operator, source)
                        VALUES ('P' || nextval('policy_id_seq'), '청년월세지원', 'RENT_SUPPORT', '국토부', 'MANUAL')
                        RETURNING id
                        """).getSingleResult();
    }

    private ApplicationView create() {
        return service.create(ownerId, planId, new CreateRequest(newPolicy(), "복지로"));
    }

    @Test
    @DisplayName("신청 건은 준비 상태로 만들어진다")
    void should_create_in_preparing_state() {
        ApplicationView view = create();

        assertThat(view.status()).isEqualTo(ApplicationStatus.PREPARING);
        assertThat(view.submittedAt()).isNull();
    }

    @Test
    @DisplayName("같은 계획에서 같은 정책을 두 번 신청할 수 없다")
    void should_reject_duplicate_application() {
        Long policyId = newPolicy();
        service.create(ownerId, planId, new CreateRequest(policyId, null));
        em.flush();

        assertThatThrownBy(() -> service.create(ownerId, planId, new CreateRequest(policyId, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("남의 계획에는 신청 건을 만들 수 없다")
    void should_reject_other_members_plan() {
        Long stranger = ownerId + 9999;

        assertThatThrownBy(() -> service.create(stranger, planId, new CreateRequest(newPolicy(), null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("제출로 옮기면 제출 시각이 찍힌다")
    void should_stamp_submitted_at() {
        ApplicationView created = create();

        ApplicationView updated = service.update(
                ownerId, planId, created.id(), new UpdateRequest(ApplicationStatus.SUBMITTED, null, null, null, null));

        assertThat(updated.submittedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("상태를 오가도 최초 제출 시각을 덮어쓰지 않는다")
    void should_keep_first_submitted_at() {
        ApplicationView created = create();
        service.update(
                ownerId, planId, created.id(), new UpdateRequest(ApplicationStatus.SUBMITTED, null, null, null, null));
        service.update(
                ownerId, planId, created.id(), new UpdateRequest(ApplicationStatus.PREPARING, null, null, null, null));

        ApplicationView again = serviceAt(NOW.plus(Duration.ofDays(3)))
                .update(
                        ownerId,
                        planId,
                        created.id(),
                        new UpdateRequest(ApplicationStatus.SUBMITTED, null, null, null, null));

        assertThat(again.submittedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("승인 결과를 기록한다")
    void should_record_approval() {
        ApplicationView created = create();

        ApplicationView updated = service.update(
                ownerId,
                planId,
                created.id(),
                new UpdateRequest(ApplicationStatus.APPROVED, null, null, 45_000_000L, new BigDecimal("1.300")));

        assertThat(updated.approvedAmount()).isEqualTo(45_000_000L);
        assertThat(updated.resultAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("거절은 어느 단계에서 막혔는지 없이 기록할 수 없다")
    void should_require_reject_stage() {
        ApplicationView created = create();

        assertThatThrownBy(() -> service.update(
                        ownerId,
                        planId,
                        created.id(),
                        new UpdateRequest(ApplicationStatus.REJECTED, null, null, null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("은행에서 막히면 다른 은행을 권한다")
    void should_guide_to_other_bank_on_bank_rejection() {
        ApplicationView created = create();

        ApplicationView updated = service.update(
                ownerId,
                planId,
                created.id(),
                new UpdateRequest(ApplicationStatus.REJECTED, RejectStage.BANK, "CREDIT", null, null));

        assertThat(updated.nextAction()).contains("다른 은행");
    }

    @Test
    @DisplayName("거절 사유와 함께 안내문 또는 보완 서류 근거를 저장한다")
    void should_store_rejection_evidence() {
        ApplicationView created = create();

        ApplicationView updated = service.update(
                ownerId,
                planId,
                created.id(),
                new UpdateRequest(
                        ApplicationStatus.REJECTED,
                        RejectStage.DOCUMENT,
                        "MISSING_INCOME",
                        null,
                        null,
                        "재직증명서와 소득금액증명원을 보완해 주세요."));

        assertThat(updated.rejectionEvidence()).isEqualTo("재직증명서와 소득금액증명원을 보완해 주세요.");
    }

    @Test
    @DisplayName("보증기관에서 막히면 은행을 바꿔도 소용없다고 알린다")
    void should_warn_bank_change_is_useless_on_guarantee_rejection() {
        ApplicationView created = create();

        ApplicationView updated = service.update(
                ownerId,
                planId,
                created.id(),
                new UpdateRequest(ApplicationStatus.REJECTED, RejectStage.GUARANTEE, "LTV", null, null));

        assertThat(updated.nextAction()).contains("은행을 바꿔도");
        assertThat(updated.nextAction()).isNotEqualTo(RejectGuidance.forStage(RejectStage.BANK));
    }

    @Test
    @DisplayName("승인으로 바뀌면 이전 거절 기록을 지운다")
    void should_clear_rejection_when_approved() {
        ApplicationView created = create();
        service.update(
                ownerId,
                planId,
                created.id(),
                new UpdateRequest(ApplicationStatus.REJECTED, RejectStage.DOCUMENT, "MISSING", null, null));

        ApplicationView approved = service.update(
                ownerId,
                planId,
                created.id(),
                new UpdateRequest(ApplicationStatus.APPROVED, null, null, 10_000_000L, null));

        assertThat(approved.rejectStage()).isNull();
        assertThat(approved.nextAction()).isNull();
    }

    @Test
    @DisplayName("계획별 신청 목록을 최신순으로 준다")
    void should_list_by_plan() {
        create();
        create();
        em.flush();

        assertThat(service.list(ownerId, planId).applications()).hasSize(2);
    }

    @Test
    @DisplayName("없는 신청 건은 찾지 못했다고 알린다")
    void should_fail_on_missing_application() {
        assertThatThrownBy(() -> service.update(
                        ownerId,
                        planId,
                        999_999L,
                        new UpdateRequest(ApplicationStatus.SUBMITTED, null, null, null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("다른 계획의 신청 건은 내 계획 경로로 수정할 수 없다")
    void should_reject_application_from_other_plan() {
        ApplicationView mine = create();

        Long otherPlanId = (Long)
                em.createNativeQuery("INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'WOLSE') RETURNING id")
                        .setParameter("uid", ownerId)
                        .getSingleResult();
        em.flush();

        // 내 계획 경로에 남의 신청 건 ID 를 끼워 넣는 시도
        assertThatThrownBy(() -> service.update(
                        ownerId,
                        otherPlanId,
                        mine.id(),
                        new UpdateRequest(ApplicationStatus.SUBMITTED, null, null, null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("결과 상태에서 되돌리면 이전 결과가 지워진다")
    void should_clear_outcome_when_reverted() {
        ApplicationView created = create();
        service.update(
                ownerId,
                planId,
                created.id(),
                new UpdateRequest(ApplicationStatus.REJECTED, RejectStage.BANK, "CREDIT", null, null));

        ApplicationView back = service.update(
                ownerId, planId, created.id(), new UpdateRequest(ApplicationStatus.SCREENING, null, null, null, null));

        assertThat(back.rejectStage()).isNull();
        assertThat(back.nextAction()).isNull();
    }

    @Test
    @DisplayName("승인에서 준비로 되돌리면 승인 금액도 지워진다")
    void should_clear_approved_amount_when_reverted() {
        ApplicationView created = create();
        service.update(
                ownerId,
                planId,
                created.id(),
                new UpdateRequest(ApplicationStatus.APPROVED, null, null, 45_000_000L, null));

        ApplicationView back = service.update(
                ownerId, planId, created.id(), new UpdateRequest(ApplicationStatus.PREPARING, null, null, null, null));

        assertThat(back.approvedAmount()).isNull();
    }

    @Test
    @DisplayName("없는 정책을 신청하면 중복이 아니라 정책 없음으로 알린다")
    void should_not_disguise_missing_policy_as_duplicate() {
        assertThatThrownBy(() -> service.create(ownerId, planId, new CreateRequest(999_999L, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode()).isEqualTo(ErrorCode.POLICY_NOT_FOUND));
    }
}
