package com.homerun.domain.diagnosis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "cost_estimate")
public class CostEstimate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(nullable = false)
    private long deposit;

    @Column(name = "moving_cost", nullable = false)
    private long movingCost;

    @Column(name = "brokerage_fee", nullable = false)
    private long brokerageFee;

    @Column(name = "guarantee_fee", nullable = false)
    private long guaranteeFee;

    @Column(name = "stamp_tax", nullable = false)
    private long stampTax;

    @Column(name = "emergency_reserve", nullable = false)
    private long emergencyReserve;

    @Column(name = "total_required", nullable = false)
    private long totalRequired;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CostEstimate() {}

    public CostEstimate(
            Long planId,
            long deposit,
            long movingCost,
            long brokerageFee,
            long guaranteeFee,
            long stampTax,
            long emergencyReserve,
            long totalRequired,
            Instant createdAt) {
        this.planId = planId;
        this.deposit = deposit;
        this.movingCost = movingCost;
        this.brokerageFee = brokerageFee;
        this.guaranteeFee = guaranteeFee;
        this.stampTax = stampTax;
        this.emergencyReserve = emergencyReserve;
        this.totalRequired = totalRequired;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public long getDeposit() {
        return deposit;
    }

    public long getMovingCost() {
        return movingCost;
    }

    public long getBrokerageFee() {
        return brokerageFee;
    }

    public long getGuaranteeFee() {
        return guaranteeFee;
    }

    public long getStampTax() {
        return stampTax;
    }

    public long getEmergencyReserve() {
        return emergencyReserve;
    }

    public long getTotalRequired() {
        return totalRequired;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
