package com.homerun.domain.plan.entity;

import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.plan.type.PlanStatus;
import com.homerun.domain.plan.type.StartSituation;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "plan")
@Data
@NoArgsConstructor
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "lease_type", nullable = false, length = 20)
    private LeaseType leaseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_situation", length = 30)
    private StartSituation startSituation;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 20)
    private PlanStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private PlanStatus status;

    @Column(name = "is_favorite")
    private Boolean isFavorite;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "last_step_code", length = 50)
    private String lastStepCode;

    @Column(name = "last_task_code", length = 50)
    private String lastTaskCode;

    public static Plan create(Long memberId, LeaseType leaseType) {
        Plan plan = new Plan();

        plan.userId = memberId;
        plan.leaseType = leaseType;
        plan.startSituation = null;
        plan.stage = PlanStage.BENCH;
        plan.status = PlanStatus.ACTIVE;
        plan.isFavorite = false;

        return plan;
    }

    public static Plan create(Long memberId, LeaseType leaseType, LocalDate createdAt) {
        Plan plan = new Plan();

        plan.userId = memberId;
        plan.leaseType = leaseType;

        plan.stage = PlanStage.BENCH;
        plan.status = PlanStatus.ACTIVE;
        plan.isFavorite = false;

        return plan;
    }

    @PrePersist
    protected void onCreate() {

        OffsetDateTime now = OffsetDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (stage == null) {
            stage = PlanStage.BENCH;
        }

        if (status == null) {
            status = PlanStatus.ACTIVE;
        }

        if (isFavorite == null) {
            isFavorite = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getLastLocationCode() {
        return "";
    }

    public LocalDate getTargetMoveDate() {
        return null;
    }

    public void verifyOwner(Long memberId) {}

    public PlanStage getLastVisitedStage() {
        return null;
    }
}
