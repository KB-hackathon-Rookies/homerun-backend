package com.homerun.domain.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.consent.ConsentDtos.IssueRequest;
import com.homerun.domain.consent.ConsentDtos.IssueResponse;
import com.homerun.domain.consent.ConsentDtos.MemberConsentStatus;
import com.homerun.domain.plan.PlanRepository;
import com.homerun.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ConsentServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

    private final ConsentTokenRepository tokens;
    private final HouseholdMemberRepository members;
    private final PlanRepository plans;
    private final EntityManager em;
    private final ConsentService service;

    /** 이 테스트가 만든 계획의 소유자. verifyOwner 를 통과시키려면 함께 넘겨야 한다. */
    private Long ownerId;

    ConsentServiceTest(
            @Autowired ConsentTokenRepository tokens,
            @Autowired HouseholdMemberRepository members,
            @Autowired PlanRepository plans,
            @Autowired EntityManager em) {
        this.tokens = tokens;
        this.members = members;
        this.plans = plans;
        this.em = em;
        this.service = new ConsentService(tokens, members, plans, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ConsentService serviceAt(Instant instant) {
        return new ConsentService(tokens, members, plans, Clock.fixed(instant, ZoneOffset.UTC));
    }

    /** plan 은 app_user 를 참조한다. 테스트가 쓸 최소한의 행만 직접 넣는다. */
    private Long newPlan() {
        Long userId = (Long) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, nickname)
                        VALUES ('KAKAO', 'test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult();
        ownerId = userId;
        Long planId = (Long)
                em.createNativeQuery("INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'WOLSE') RETURNING id")
                        .setParameter("uid", userId)
                        .getSingleResult();
        em.flush();
        em.clear();
        return planId;
    }

    /** 같은 사람의 두 번째 계획. 소유권은 통과하되 가구원 소속만 어긋나는 상황을 만든다. */
    private Long anotherPlanOf(Long userId) {
        Long planId = (Long)
                em.createNativeQuery("INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'WOLSE') RETURNING id")
                        .setParameter("uid", userId)
                        .getSingleResult();
        em.flush();
        em.clear();
        return planId;
    }

    private HouseholdMember newMember(Long planId) {
        return members.save(new HouseholdMember(planId, "부", true));
    }

    @Test
    @DisplayName("토큰 원문은 저장하지 않고 해시만 남긴다")
    void should_store_only_hash() {
        Long planId = newPlan();
        HouseholdMember member = newMember(planId);

        IssueResponse issued = service.issue(ownerId, planId, new IssueRequest(member.id(), null, "소득 확인"));
        em.flush();

        assertThat(issued.token()).isNotBlank();
        assertThat(tokens.findById(issued.consentId()).orElseThrow())
                .satisfies(token -> assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(72))));
        // 원문으로는 못 찾고 해시로만 찾힌다
        assertThat(tokens.findByTokenHash(issued.token())).isEmpty();
    }

    @Test
    @DisplayName("발급한 링크로 가구원 화면을 열 수 있다")
    void should_open_view_with_issued_token() {
        Long planId = newPlan();
        HouseholdMember member = newMember(planId);
        IssueResponse issued = service.issue(ownerId, planId, new IssueRequest(member.id(), null, "소득 확인"));

        assertThat(service.view(issued.token()))
                .satisfies(view -> assertThat(view.relation()).isEqualTo("부"))
                .satisfies(view -> assertThat(view.purpose()).isEqualTo("소득 확인"));
    }

    @Test
    @DisplayName("72시간이 지나면 만료로 끊는다")
    void should_expire_after_72_hours() {
        Long planId = newPlan();
        HouseholdMember member = newMember(planId);
        IssueResponse issued = service.issue(ownerId, planId, new IssueRequest(member.id(), null, "소득 확인"));
        em.flush();

        ConsentService later = serviceAt(NOW.plus(Duration.ofHours(72)));

        assertThatThrownBy(() -> later.view(issued.token()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode())
                        .isEqualTo(com.homerun.global.exception.ErrorCode.CONSENT_TOKEN_EXPIRED));
    }

    @Test
    @DisplayName("한 번 응답한 링크는 다시 쓸 수 없다")
    void should_reject_reused_token() {
        Long planId = newPlan();
        HouseholdMember member = newMember(planId);
        IssueResponse issued = service.issue(ownerId, planId, new IssueRequest(member.id(), null, "소득 확인"));

        service.respond(issued.token(), true);
        em.flush();

        assertThatThrownBy(() -> service.respond(issued.token(), false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode())
                        .isEqualTo(com.homerun.global.exception.ErrorCode.CONSENT_TOKEN_INVALID));
    }

    @Test
    @DisplayName("동의하면 가구원이 확인 완료로 바뀐다")
    void should_mark_member_verified_on_agree() {
        Long planId = newPlan();
        HouseholdMember member = newMember(planId);
        IssueResponse issued = service.issue(ownerId, planId, new IssueRequest(member.id(), null, "소득 확인"));

        service.respond(issued.token(), true);
        em.flush();

        assertThat(members.findById(member.id()).orElseThrow().verified()).isTrue();
    }

    @Test
    @DisplayName("거절하면 링크는 소모되지만 확인 완료가 되지 않는다")
    void should_not_verify_on_reject() {
        Long planId = newPlan();
        HouseholdMember member = newMember(planId);
        IssueResponse issued = service.issue(ownerId, planId, new IssueRequest(member.id(), null, "소득 확인"));

        service.respond(issued.token(), false);
        em.flush();

        assertThat(members.findById(member.id()).orElseThrow().verified()).isFalse();
    }

    @Test
    @DisplayName("재발급하면 이전 링크는 폐기된다")
    void should_revoke_previous_on_reissue() {
        Long planId = newPlan();
        HouseholdMember member = newMember(planId);
        IssueResponse first = service.issue(ownerId, planId, new IssueRequest(member.id(), null, "소득 확인"));

        IssueResponse second = service.reissue(ownerId, planId, first.consentId());
        em.flush();

        assertThat(second.token()).isNotEqualTo(first.token());
        assertThatThrownBy(() -> service.view(first.token())).isInstanceOf(BusinessException.class);
        assertThat(service.view(second.token()).purpose()).isEqualTo("소득 확인");
    }

    @Test
    @DisplayName("확인이 필요한 가구원이 남아 있으면 allVerified 가 false 다")
    void should_report_not_all_verified() {
        Long planId = newPlan();
        newMember(planId);
        em.flush();

        assertThat(service.overview(ownerId, planId).allVerified()).isFalse();
    }

    @Test
    @DisplayName("동의가 필요 없는 가구원은 확인 여부를 따지지 않는다")
    void should_ignore_members_without_consent_requirement() {
        Long planId = newPlan();
        members.save(new HouseholdMember(planId, "모", false));
        em.flush();

        assertThat(service.overview(ownerId, planId).allVerified()).isTrue();
    }

    @Test
    @DisplayName("모두 동의하면 allVerified 가 true 가 된다")
    void should_report_all_verified_after_consent() {
        Long planId = newPlan();
        HouseholdMember member = newMember(planId);
        IssueResponse issued = service.issue(ownerId, planId, new IssueRequest(member.id(), null, "소득 확인"));
        service.respond(issued.token(), true);
        em.flush();
        em.clear();

        assertThat(service.overview(ownerId, planId).allVerified()).isTrue();
    }

    @Test
    @DisplayName("현황에는 관계만 담고 이름은 담지 않는다")
    void should_expose_relation_only() {
        Long planId = newPlan();
        newMember(planId);
        em.flush();

        assertThat(service.overview(ownerId, planId).members())
                .singleElement()
                .satisfies(status -> assertThat(status.relation()).isEqualTo("부"))
                .satisfies(status -> assertThat(status.status()).isEqualTo(ConsentStatus.PENDING));
    }

    @Test
    @DisplayName("다른 계획의 가구원에게는 링크를 발급하지 않는다")
    void should_reject_member_from_other_plan() {
        Long planId = newPlan();
        Long owner = ownerId;
        HouseholdMember member = newMember(anotherPlanOf(owner));
        em.flush();

        // 계획 소유자는 맞지만 가구원이 다른 계획 소속이다. 이걸 허용하면 한 계획의
        // 동의 링크로 다른 계획의 가구원을 확인 처리할 수 있게 된다.
        assertThatThrownBy(() -> service.issue(owner, planId, new IssueRequest(member.id(), null, "소득 확인")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("존재하지 않는 토큰은 폐기된 토큰과 같은 코드로 응답한다")
    void should_not_leak_token_existence() {
        assertThatThrownBy(() -> service.view("does-not-exist"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode())
                        .isEqualTo(com.homerun.global.exception.ErrorCode.CONSENT_TOKEN_INVALID));
    }

    @Test
    @DisplayName("응답이 끝난 가구원의 현황은 RESPONDED 다")
    void should_report_responded_status() {
        Long planId = newPlan();
        HouseholdMember member = newMember(planId);
        IssueResponse issued = service.issue(ownerId, planId, new IssueRequest(member.id(), null, "소득 확인"));
        service.respond(issued.token(), true);
        em.flush();
        em.clear();

        assertThat(service.overview(ownerId, planId).members())
                .extracting(MemberConsentStatus::status)
                .containsExactly(ConsentStatus.RESPONDED);
    }
}
