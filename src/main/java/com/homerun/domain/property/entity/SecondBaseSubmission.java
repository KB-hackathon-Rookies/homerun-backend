package com.homerun.domain.property.entity;

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
@Table(name = "second_base_submission")
public class SecondBaseSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "decision_revision", nullable = false)
    private int decisionRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resultSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SecondBaseSubmission() {}

    public SecondBaseSubmission(
            Long planId, int decisionRevision, Map<String, Object> resultSnapshot, Instant createdAt) {
        this.planId = planId;
        this.decisionRevision = decisionRevision;
        this.resultSnapshot = resultSnapshot;
        this.createdAt = createdAt;
    }

    public int getDecisionRevision() {
        return decisionRevision;
    }

    public Map<String, Object> getResultSnapshot() {
        return resultSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
