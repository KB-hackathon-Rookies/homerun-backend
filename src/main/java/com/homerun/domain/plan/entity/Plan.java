package com.homerun.domain.plan.entity;

import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.plan.type.PlanStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "plan")
public class Plan {

    public static final String CURRENT_RULE_VERSION = "1.0";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "lease_type", nullable = false, length = 20)
    private LeaseType leaseType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_visited_stage", nullable = false, length = 20)
    private PlanStage lastVisitedStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status;

    @Column(name = "last_location_code", length = 100)
    private String lastLocationCode;

    @Column(name = "rule_version", nullable = false, length = 20)
    private String ruleVersion;

    @Column(name = "target_move_date")
    private LocalDate targetMoveDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Plan() {}

    private Plan(Long memberId, LeaseType leaseType, LocalDate targetMoveDate) {
        this.memberId = memberId;
        this.leaseType = leaseType;
        this.targetMoveDate = targetMoveDate;
        this.stage = PlanStage.BENCH;
        this.lastVisitedStage = PlanStage.BENCH;
        this.status = PlanStatus.ACTIVE;
        this.ruleVersion = CURRENT_RULE_VERSION;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static Plan create(Long memberId, LeaseType leaseType, LocalDate targetMoveDate) {
        return new Plan(memberId, leaseType, targetMoveDate);
    }

    public void verifyOwner(Long requesterId) {
        if (!memberId.equals(requesterId)) {
            throw new BusinessException(ErrorCode.PLAN_ACCESS_DENIED);
        }
    }

    public void verifyRuleVersion(String requestedRuleVersion) {
        if (!ruleVersion.equals(requestedRuleVersion)) {
            throw new BusinessException(ErrorCode.RULE_VERSION_MISMATCH);
        }
    }

    public void advance() {
        verifyActive();
        stage = stage.next();
        lastVisitedStage = stage;
        lastLocationCode = null;
        touch();
    }

    public void finish() {
        verifyActive();
        if (stage != PlanStage.HOME) {
            throw new BusinessException(ErrorCode.INVALID_STAGE_TRANSITION);
        }
        status = PlanStatus.DONE;
        lastVisitedStage = PlanStage.HOME;
        lastLocationCode = null;
        touch();
    }

    public void updateLastLocation(String locationCode) {
        verifyActive();
        lastVisitedStage = stage;
        lastLocationCode = locationCode;
        touch();
    }

    public void enterStage(PlanStage targetStage, String locationCode) {
        if (targetStage.ordinal() > stage.ordinal()) {
            throw new BusinessException(ErrorCode.PLAN_STAGE_LOCKED);
        }
        lastVisitedStage = targetStage;
        lastLocationCode = locationCode;
        touch();
    }

    public void reset() {
        stage = PlanStage.BENCH;
        lastVisitedStage = PlanStage.BENCH;
        status = PlanStatus.ACTIVE;
        lastLocationCode = null;
        ruleVersion = CURRENT_RULE_VERSION;
        touch();
    }

    private void verifyActive() {
        if (status != PlanStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.PLAN_NOT_ACTIVE);
        }
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public LeaseType getLeaseType() {
        return leaseType;
    }

    public PlanStage getStage() {
        return stage;
    }

    public PlanStage getLastVisitedStage() {
        return lastVisitedStage;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public String getLastLocationCode() {
        return lastLocationCode;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public LocalDate getTargetMoveDate() {
        return targetMoveDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
