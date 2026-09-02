package com.homerun.domain.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** 정책 신청 한 건. 실제 제출은 하지 않고 준비와 상태만 관리한다(OUT-01-03). */
@Entity
@Table(name = "application")
public class PolicyApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.PREPARING;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "result_at")
    private Instant resultAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_stage")
    private RejectStage rejectStage;

    @Column(name = "reject_reason_code")
    private String rejectReasonCode;

    @Column(name = "approved_amount")
    private Long approvedAmount;

    @Column(name = "approved_rate")
    private BigDecimal approvedRate;

    protected PolicyApplication() {}

    public PolicyApplication(Long planId, Long policyId, String channel) {
        this.planId = planId;
        this.policyId = policyId;
        this.channel = channel;
    }

    public Long id() {
        return id;
    }

    public Long planId() {
        return planId;
    }

    public Long policyId() {
        return policyId;
    }

    public String channel() {
        return channel;
    }

    public ApplicationStatus status() {
        return status;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public Instant resultAt() {
        return resultAt;
    }

    public RejectStage rejectStage() {
        return rejectStage;
    }

    public String rejectReasonCode() {
        return rejectReasonCode;
    }

    public Long approvedAmount() {
        return approvedAmount;
    }

    public BigDecimal approvedRate() {
        return approvedRate;
    }

    /**
     * 진행 상태를 옮긴다.
     *
     * <p>제출과 결과 시각은 상태가 처음 그리로 옮겨갈 때만 찍는다. 사용자가 상태를 오가며
     * 고칠 수 있어서, 매번 덮어쓰면 실제 제출일을 잃는다.
     */
    void changeStatus(ApplicationStatus next, Instant now) {
        if (next == ApplicationStatus.SUBMITTED && submittedAt == null) {
            submittedAt = now;
        }
        if ((next == ApplicationStatus.APPROVED || next == ApplicationStatus.REJECTED) && resultAt == null) {
            resultAt = now;
        }
        this.status = next;
    }

    void recordApproval(Long amount, BigDecimal rate) {
        this.approvedAmount = amount;
        this.approvedRate = rate;
        this.rejectStage = null;
        this.rejectReasonCode = null;
    }

    void recordRejection(RejectStage stage, String reasonCode) {
        this.rejectStage = stage;
        this.rejectReasonCode = reasonCode;
        this.approvedAmount = null;
        this.approvedRate = null;
    }
}
