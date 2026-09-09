package com.homerun.domain.terms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_agreement")
public class UserAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terms_id", nullable = false)
    private Term term;

    @Column(nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at", nullable = false)
    private Instant agreedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected UserAgreement() {}

    private UserAgreement(Long memberId, Term term) {
        this.memberId = memberId;
        this.term = term;
        agree();
    }

    public static UserAgreement agree(Long memberId, Term term) {
        return new UserAgreement(memberId, term);
    }

    /**
     * 아직 답을 정하지 않은 상태로 만든다.
     *
     * <p>선택 약관은 "동의 안 함" 도 남겨야 한다 — 거절한 것과 물어본 적 없는 것은 다르다.
     * 만든 직후 {@link #agree()} 나 {@link #revoke()} 로 답을 정한다.
     */
    public static UserAgreement of(Long memberId, Term term) {
        return new UserAgreement(memberId, term);
    }

    /** 동의하지 않는다고 답했다. 기록은 남는다 — 물어본 적 없는 것과 구분하기 위해서다. */
    public void revoke() {
        agreed = false;
        revokedAt = Instant.now();
    }

    public void agree() {
        agreed = true;
        agreedAt = Instant.now();
        revokedAt = null;
    }

    public Term getTerm() {
        return term;
    }

    public boolean isAgreed() {
        return agreed;
    }

    public Instant getAgreedAt() {
        return agreedAt;
    }
}
