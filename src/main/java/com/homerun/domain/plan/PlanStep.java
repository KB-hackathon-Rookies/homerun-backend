package com.homerun.domain.plan;

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
import java.util.Arrays;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "plan_step")
public class PlanStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "step_code", nullable = false, length = 50)
    private String stepCode;

    @Column(name = "step_name", nullable = false, length = 200)
    private String stepName;

    @Column(nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStepStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "depends_on", nullable = false, columnDefinition = "jsonb")
    private List<String> dependsOn;

    @Column(name = "is_irreversible", nullable = false)
    private boolean irreversible;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PlanStep() {}

    private PlanStep(Long planId, PlanGate gate) {
        this.planId = planId;
        this.stepCode = gate.code();
        this.stepName = gate.displayName();
        this.sequence = gate.sequence();
        this.status = gate.dependencies().isEmpty() ? PlanStepStatus.READY : PlanStepStatus.LOCKED;
        this.dependsOn = List.copyOf(gate.dependencies());
        this.updatedAt = Instant.now();
    }

    public static List<PlanStep> defaultSteps(Long planId) {
        return Arrays.stream(PlanGate.values())
                .map(gate -> new PlanStep(planId, gate))
                .toList();
    }

    public boolean complete() {
        if (status == PlanStepStatus.DONE) {
            return false;
        }
        if (status == PlanStepStatus.LOCKED) {
            throw new BusinessException(ErrorCode.PLAN_STEP_LOCKED);
        }
        status = PlanStepStatus.DONE;
        completedAt = Instant.now();
        updatedAt = completedAt;
        return true;
    }

    public void unlockWhenDependenciesCompleted(List<String> completedStepCodes) {
        if (status == PlanStepStatus.LOCKED && completedStepCodes.containsAll(dependsOn)) {
            status = PlanStepStatus.READY;
            updatedAt = Instant.now();
        }
    }

    public void requireRecalculation() {
        if (status == PlanStepStatus.DONE || status == PlanStepStatus.DOING) {
            status = PlanStepStatus.RECALC_REQUIRED;
            completedAt = null;
            updatedAt = Instant.now();
        }
    }

    public void reset() {
        status = dependsOn.isEmpty() ? PlanStepStatus.READY : PlanStepStatus.LOCKED;
        completedAt = null;
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public String getStepCode() {
        return stepCode;
    }

    public String getStepName() {
        return stepName;
    }

    public int getSequence() {
        return sequence;
    }

    public PlanStepStatus getStatus() {
        return status;
    }

    public List<String> getDependsOn() {
        return List.copyOf(dependsOn);
    }

    public boolean isIrreversible() {
        return irreversible;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
