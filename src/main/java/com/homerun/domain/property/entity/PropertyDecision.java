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

    protected PropertyDecision() {}

    public PropertyDecision(Long planId, Long propertyId, Long consultationId, Instant decidedAt) {
        this.planId = planId;
        decide(propertyId, consultationId, decidedAt);
    }

    public void decide(Long propertyId, Long consultationId, Instant decidedAt) {
        this.propertyId = propertyId;
        this.consultationId = consultationId;
        this.decidedAt = decidedAt;
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
}
