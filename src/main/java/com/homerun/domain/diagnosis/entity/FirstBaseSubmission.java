package com.homerun.domain.diagnosis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FirstBaseSubmission() {}

    public FirstBaseSubmission(Long planId, int inputRevision, Long diagnosisId) {
        this.planId = planId;
        this.inputRevision = inputRevision;
        this.diagnosisId = diagnosisId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
