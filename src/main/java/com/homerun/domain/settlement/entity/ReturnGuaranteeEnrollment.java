package com.homerun.domain.settlement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** 반환보증 가입 상태(FR-H1-03). 계획당 1건. */
@Entity
@Table(name = "return_guarantee")
public class ReturnGuaranteeEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(nullable = false)
    private boolean enrolled;

    @Column(name = "fee_paid", nullable = false)
    private boolean feePaid;

    @Column(name = "enrolled_at")
    private LocalDate enrolledAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ReturnGuaranteeEnrollment() {}

    public ReturnGuaranteeEnrollment(Long planId) {
        this.planId = planId;
    }

    public void update(boolean enrolled, boolean feePaid, LocalDate enrolledAt) {
        this.enrolled = enrolled;
        this.feePaid = feePaid;
        this.enrolledAt = enrolledAt;
    }

    public Long getPlanId() {
        return planId;
    }

    public boolean isEnrolled() {
        return enrolled;
    }

    public boolean isFeePaid() {
        return feePaid;
    }

    public LocalDate getEnrolledAt() {
        return enrolledAt;
    }
}
