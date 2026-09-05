package com.homerun.domain.property.entity;

import com.homerun.domain.property.dto.request.BankConsultationRequest;
import com.homerun.domain.property.type.CollateralMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bank_consultation")
public class BankConsultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "branch_name", length = 100)
    private String branchName;

    @Column(name = "policy_id")
    private Long policyId;

    @Column(name = "guarantee_agency_id")
    private Long guaranteeAgencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "collateral_method", nullable = false, length = 30)
    private CollateralMethod collateralMethod;

    @Column(name = "approved_limit")
    private Long approvedLimit;

    @Column(name = "quoted_rate", precision = 6, scale = 3)
    private BigDecimal quotedRate;

    @Column(name = "consulted_at", nullable = false)
    private LocalDate consultedAt;

    @Column(columnDefinition = "text")
    private String memo;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected BankConsultation() {}

    public BankConsultation(Long planId, Long propertyId, BankConsultationRequest request) {
        this.planId = planId;
        this.propertyId = propertyId;
        this.bankName = request.bankName();
        this.branchName = request.branchName();
        this.policyId = request.policyId();
        this.guaranteeAgencyId = request.guaranteeAgencyId();
        this.collateralMethod = request.collateralMethod();
        this.approvedLimit = request.approvedLimit();
        this.quotedRate = request.quotedRate();
        this.consultedAt = request.consultedAt();
        this.memo = request.memo();
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

    public String getBankName() {
        return bankName;
    }

    public String getBranchName() {
        return branchName;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public Long getGuaranteeAgencyId() {
        return guaranteeAgencyId;
    }

    public CollateralMethod getCollateralMethod() {
        return collateralMethod;
    }

    public Long getApprovedLimit() {
        return approvedLimit;
    }

    public BigDecimal getQuotedRate() {
        return quotedRate;
    }

    public LocalDate getConsultedAt() {
        return consultedAt;
    }

    public String getMemo() {
        return memo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
