package com.homerun.domain.plan.entity;

import com.homerun.domain.plan.type.DiagnosisInputStep;
import com.homerun.domain.plan.type.PlanInputStepStatus;
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

@Entity
@Table(name = "plan_input_step")
public class PlanInputStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_code", nullable = false, length = 40)
    private DiagnosisInputStep stepCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanInputStepStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PlanInputStep() {}

    public PlanInputStep(Long planId, DiagnosisInputStep stepCode) {
        this.planId = planId;
        this.stepCode = stepCode;
        complete();
    }

    public void complete() {
        status = PlanInputStepStatus.COMPLETED;
        completedAt = Instant.now();
        updatedAt = completedAt;
    }

    public void skip() {
        status = PlanInputStepStatus.SKIPPED;
        completedAt = null;
        updatedAt = Instant.now();
    }

    public DiagnosisInputStep getStepCode() {
        return stepCode;
    }

    public PlanInputStepStatus getStatus() {
        return status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
