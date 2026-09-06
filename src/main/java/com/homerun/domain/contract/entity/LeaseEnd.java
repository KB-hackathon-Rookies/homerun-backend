package com.homerun.domain.contract.entity;

import com.homerun.domain.contract.type.LeaseDecision;
import com.homerun.domain.contract.type.RenewalMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** 갱신·퇴거 결정(DR-22 lease_end). 계획당 1건. */
@Entity
@Table(name = "lease_end")
public class LeaseEnd {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaseDecision decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "renewal_method", length = 20)
    private RenewalMethod renewalMethod;

    @Column(name = "claim_right_used", nullable = false)
    private boolean claimRightUsed;

    @Column(name = "notice_sent_at")
    private LocalDate noticeSentAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected LeaseEnd() {}

    public LeaseEnd(Long planId) {
        this.planId = planId;
    }

    /**
     * 결정을 반영한다. 계약갱신청구권을 고르면 청구권 사용으로 기록한다(FR-H9-02: 재사용 불가).
     */
    public void decide(LeaseDecision decision, RenewalMethod renewalMethod, LocalDate noticeSentAt, Instant decidedAt) {
        this.decision = decision;
        this.renewalMethod = renewalMethod;
        this.claimRightUsed = renewalMethod == RenewalMethod.CLAIM;
        this.noticeSentAt = noticeSentAt;
        this.decidedAt = decidedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public LeaseDecision getDecision() {
        return decision;
    }

    public RenewalMethod getRenewalMethod() {
        return renewalMethod;
    }

    public boolean isClaimRightUsed() {
        return claimRightUsed;
    }

    public LocalDate getNoticeSentAt() {
        return noticeSentAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
