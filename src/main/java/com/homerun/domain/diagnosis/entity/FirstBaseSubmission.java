package com.homerun.domain.diagnosis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "first_base_submission")
public class FirstBaseSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "input_revision", nullable = false)
    private int inputRevision;

    @Column(name = "diagnosis_id", nullable = false)
    private Long diagnosisId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> resultSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FirstBaseSubmission() {}

    public FirstBaseSubmission(Long planId, int inputRevision, Long diagnosisId, Map<String, Object> resultSnapshot) {
        this.planId = planId;
        this.inputRevision = inputRevision;
        this.diagnosisId = diagnosisId;
        this.resultSnapshot = resultSnapshot;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public int getInputRevision() {
        return inputRevision;
    }

    public Long getDiagnosisId() {
        return diagnosisId;
    }

    public Map<String, Object> getResultSnapshot() {
        return resultSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
