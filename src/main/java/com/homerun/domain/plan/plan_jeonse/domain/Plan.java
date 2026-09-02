package com.homerun.domain.plan.plan_jeonse.domain;

import com.homerun.domain.plan.enums.LeaseType;
import com.homerun.domain.plan.enums.PlanStage;
import com.homerun.domain.plan.enums.PlanStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
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

    @Column(name = "start_situation", length = 30)
    private String startSituation;

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
}