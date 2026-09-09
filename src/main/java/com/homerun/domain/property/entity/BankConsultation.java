package com.homerun.domain.property.entity;

import com.homerun.domain.property.dto.request.BankConsultationRequest;
import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.ConsultationResultStatus;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.property.type.RejectionCategory;
import com.homerun.domain.property.type.RejectionStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "bank_consultation",
        // 자연키는 (계획 + 매물 + 은행 + 상품)이다. 실제 강제는 V74 유니크 제약이 하지만,
        // 여기에 적어 두지 않으면 매핑만 보고 지점·상담일이 키에 든다고 오해하기 쉽다.
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_bank_consultation_plan_property_bank_product",
                        columnNames = {"plan_id", "property_id", "bank_name", "loan_product"}))
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
    @Column(name = "result_status", nullable = false, length = 30)
    private ConsultationResultStatus resultStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_product", nullable = false, length = 30)
    private ConsultedLoanProduct loanProduct;

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

    /** 거절일 때만 채운다(BR-24). 어디서 막혔는가. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_stage", length = 20)
    private RejectionStage rejectionStage;

    /** 거절일 때만 채운다(BR-24). 무엇 때문에 막혔는가. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_category", length = 20)
    private RejectionCategory rejectionCategory;

    @Column(name = "rejection_note", columnDefinition = "text")
    private String rejectionNote;

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
        this.resultStatus = request.resultStatus();
        this.loanProduct = request.loanProduct();
        this.collateralMethod = request.collateralMethod();
        this.approvedLimit = request.approvedLimit();
        this.quotedRate = request.quotedRate();
        this.consultedAt = request.consultedAt();
        this.memo = request.memo();
        this.rejectionStage = request.rejectionStage();
        this.rejectionCategory = request.rejectionCategory();
        this.rejectionNote = request.rejectionNote();
    }

    /**
     * 같은 (은행 + 상품) 상담을 다시 기록할 때 값만 덮어쓴다. 키(planId·propertyId·bankName·
     * loanProduct)와 createdAt 은 그대로 두고 결과·담보·한도·금리·거절정보 등 최신 값으로 갱신한다.
     */
    public void applyUpdate(BankConsultationRequest request) {
        this.branchName = request.branchName();
        this.policyId = request.policyId();
        this.guaranteeAgencyId = request.guaranteeAgencyId();
        this.resultStatus = request.resultStatus();
        this.collateralMethod = request.collateralMethod();
        this.approvedLimit = request.approvedLimit();
        this.quotedRate = request.quotedRate();
        this.consultedAt = request.consultedAt();
        this.memo = request.memo();
        this.rejectionStage = request.rejectionStage();
        this.rejectionCategory = request.rejectionCategory();
        this.rejectionNote = request.rejectionNote();
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

    public ConsultationResultStatus getResultStatus() {
        return resultStatus;
    }

    public ConsultedLoanProduct getLoanProduct() {
        return loanProduct;
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

    public boolean isSelectable() {
        return resultStatus == ConsultationResultStatus.POSSIBLE;
    }

    public com.homerun.domain.property.type.RejectionStage getRejectionStage() {
        return rejectionStage;
    }

    public com.homerun.domain.property.type.RejectionCategory getRejectionCategory() {
        return rejectionCategory;
    }

    public String getRejectionNote() {
        return rejectionNote;
    }
}
