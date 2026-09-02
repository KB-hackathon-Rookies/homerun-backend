package com.homerun.domain.consent.entity;

import com.homerun.domain.consent.type.ConsentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 가구원 동의를 받기 위한 일회용 토큰.
 *
 * <p>원문을 저장하지 않는다. 해시만 남기므로 DB 가 새어도 링크를 되살릴 수 없다(SEC-01-03).
 * 원문은 발급 응답에 한 번만 실리고 그 뒤로는 어디에도 남지 않는다.
 */
@Entity
@Table(name = "consent_token")
public class ConsentToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "policy_id")
    private Long policyId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ConsentToken() {}

    public ConsentToken(
            Long planId, Long memberId, Long policyId, String tokenHash, String purpose, Instant expiresAt) {
        this.planId = planId;
        this.memberId = memberId;
        this.policyId = policyId;
        this.tokenHash = tokenHash;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }

    public Long id() {
        return id;
    }

    public Long planId() {
        return planId;
    }

    public Long memberId() {
        return memberId;
    }

    public Long policyId() {
        return policyId;
    }

    public String purpose() {
        return purpose;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant usedAt() {
        return usedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    /** 아직 쓸 수 있는 링크인가. 만료·사용·폐기 중 하나라도 걸리면 못 쓴다. */
    public boolean usableAt(Instant now) {
        return usedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    public ConsentStatus statusAt(Instant now) {
        if (revokedAt != null) {
            return ConsentStatus.REVOKED;
        }
        if (usedAt != null) {
            return ConsentStatus.RESPONDED;
        }
        return now.isBefore(expiresAt) ? ConsentStatus.PENDING : ConsentStatus.EXPIRED;
    }

    public void markUsed(Instant at) {
        this.usedAt = at;
    }

    public void revoke(Instant at) {
        this.revokedAt = at;
    }
}
