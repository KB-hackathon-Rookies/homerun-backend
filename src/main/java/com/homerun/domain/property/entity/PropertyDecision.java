package com.homerun.domain.property.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "property_decision")
public class PropertyDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false, unique = true)
    private Long planId;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "consultation_id", nullable = false)
    private Long consultationId;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "decision_revision", nullable = false)
    private int revision;

    protected PropertyDecision() {}

    public PropertyDecision(Long planId, Long propertyId, Long consultationId, Instant decidedAt) {
        this.planId = planId;
        this.propertyId = propertyId;
        this.consultationId = consultationId;
        this.decidedAt = decidedAt;
        this.revision = 1;
    }

    public boolean decide(Long propertyId, Long consultationId, Instant decidedAt) {
        if (this.propertyId.equals(propertyId) && this.consultationId.equals(consultationId)) {
            return false;
        }
        this.propertyId = propertyId;
        this.consultationId = consultationId;
        this.decidedAt = decidedAt;
        revision++;
        return true;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public Long getConsultationId() {
        return consultationId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public int getRevision() {
        return revision;
    }
}
