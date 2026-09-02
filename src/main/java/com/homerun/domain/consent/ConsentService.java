package com.homerun.domain.consent;

import com.homerun.domain.consent.ConsentDtos.ConsentOverview;
import com.homerun.domain.consent.ConsentDtos.ConsentView;
import com.homerun.domain.consent.ConsentDtos.IssueRequest;
import com.homerun.domain.consent.ConsentDtos.IssueResponse;
import com.homerun.domain.consent.ConsentDtos.MemberConsentStatus;
import com.homerun.domain.plan.PlanRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가구원 동의 오케스트레이션(FAM-01).
 *
 * <p>서비스가 동의를 대신 받지 않는다. 실제 자격 판정은 신청기관이 하고, 우리는 가구원에게
 * 설명을 전달하고 응답 여부만 추적한다(FAM-01-02).
 */
@Service
public class ConsentService {

    /** 링크 유효기간. 길게 두면 유출된 링크가 오래 살아 있다(SEC-01-03). */
    private static final Duration TOKEN_TTL = Duration.ofHours(72);

    private static final int TOKEN_BYTES = 32;

    private final ConsentTokenRepository tokens;
    private final HouseholdMemberRepository members;
    private final PlanRepository plans;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ConsentService(
            ConsentTokenRepository tokens, HouseholdMemberRepository members, PlanRepository plans, Clock clock) {
        this.tokens = tokens;
        this.members = members;
        this.plans = plans;
        this.clock = clock;
    }

    /** 남의 계획을 건드리지 못하게 한다(SEC-01-04). */
    private void verifyOwner(Long memberId, Long planId) {
        plans.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND))
                .verifyOwner(memberId);
    }

    /**
     * 일회용 동의 링크를 만든다(FAM-01-02).
     *
     * <p>원문 토큰은 이 반환값에만 실린다. 저장하는 것은 해시뿐이라 이후에는 서버도 링크를
     * 복원할 수 없다.
     */
    @Transactional
    public IssueResponse issue(Long memberId, Long planId, IssueRequest request) {
        verifyOwner(memberId, planId);
        HouseholdMember member = members.findById(request.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.HOUSEHOLD_MEMBER_NOT_FOUND));
        if (!member.planId().equals(planId)) {
            throw new BusinessException(ErrorCode.PLAN_ACCESS_DENIED);
        }

        String raw = newToken();
        Instant expiresAt = Instant.now(clock).plus(TOKEN_TTL);
        ConsentToken token = tokens.save(new ConsentToken(
                planId, request.memberId(), request.policyId(), hash(raw), request.purpose(), expiresAt));

        return new IssueResponse(token.id(), raw, expiresAt);
    }

    /**
     * 가구원이 링크를 열었을 때 보여줄 내용(FAM-01-02).
     *
     * <p>관계와 목적만 준다. 누구의 어떤 정보인지는 담지 않는다(SEC-01-05).
     */
    @Transactional(readOnly = true)
    public ConsentView view(String rawToken) {
        ConsentToken token = requireUsable(rawToken);
        String relation = Optional.ofNullable(token.memberId())
                .flatMap(members::findById)
                .map(HouseholdMember::relation)
                .orElse("가구원");
        return new ConsentView(relation, token.purpose(), token.expiresAt());
    }

    /**
     * 가구원의 응답을 기록한다.
     *
     * <p>동의하면 확인 완료로 표시한다. 거절해도 토큰은 소모된다. 같은 링크로 여러 번 답을
     * 바꾸게 두면 어느 것이 진짜인지 알 수 없다.
     */
    @Transactional
    public void respond(String rawToken, boolean agreed) {
        Instant now = Instant.now(clock);
        ConsentToken token = requireUsable(rawToken);
        token.markUsed(now);

        if (agreed && token.memberId() != null) {
            members.findById(token.memberId()).ifPresent(member -> member.markVerified(now));
        }
    }

    /**
     * 링크를 다시 보낸다.
     *
     * <p>이전 링크는 폐기한다. 살려 두면 유효한 링크가 둘이 되어 하나가 유출돼도 알 수 없다.
     */
    @Transactional
    public IssueResponse reissue(Long memberId, Long planId, Long consentId) {
        verifyOwner(memberId, planId);
        ConsentToken previous =
                tokens.findById(consentId).orElseThrow(() -> new BusinessException(ErrorCode.CONSENT_NOT_FOUND));
        previous.revoke(Instant.now(clock));

        return issue(
                memberId,
                previous.planId(),
                new ConsentDtos.IssueRequest(previous.memberId(), previous.policyId(), previous.purpose()));
    }

    /**
     * 계획 단위 동의 현황(FAM-01-01, FAM-01-04).
     *
     * <p>확인이 끝나지 않은 가구원이 하나라도 있으면 {@code allVerified} 가 false 다. 호출한
     * 쪽은 이걸 보고 자격을 확정하지 말고 추가확인 상태로 남겨야 한다.
     */
    @Transactional(readOnly = true)
    public ConsentOverview overview(Long memberId, Long planId) {
        verifyOwner(memberId, planId);
        Instant now = Instant.now(clock);
        List<MemberConsentStatus> statuses = members.findByPlanIdOrderById(planId).stream()
                .map(member -> new MemberConsentStatus(
                        member.id(),
                        member.relation(),
                        member.consentRequired(),
                        member.verified(),
                        latestStatus(member.id(), now)))
                .toList();

        boolean allVerified =
                statuses.stream().filter(MemberConsentStatus::consentRequired).allMatch(MemberConsentStatus::verified);

        return new ConsentOverview(allVerified, statuses);
    }

    private ConsentStatus latestStatus(Long memberId, Instant now) {
        return tokens.findByMemberIdOrderByIdDesc(memberId).stream()
                .max(Comparator.comparing(ConsentToken::id))
                .map(token -> token.statusAt(now))
                .orElse(ConsentStatus.PENDING);
    }

    private ConsentToken requireUsable(String rawToken) {
        // 존재하지 않는 토큰과 폐기된 토큰을 같은 코드로 응답한다. 구분해 주면 링크를 넣어
        // 보며 유효한 값을 찾아내는 시도에 힌트가 된다.
        ConsentToken token = tokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.CONSENT_TOKEN_INVALID));
        Instant now = Instant.now(clock);
        if (token.revokedAt() != null || token.usedAt() != null) {
            throw new BusinessException(ErrorCode.CONSENT_TOKEN_INVALID);
        }
        if (!now.isBefore(token.expiresAt())) {
            throw new BusinessException(ErrorCode.CONSENT_TOKEN_EXPIRED);
        }
        return token;
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 16진수 64자. consent_token.token_hash 길이와 맞춘다. */
    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
        }
    }
}
