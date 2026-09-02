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
