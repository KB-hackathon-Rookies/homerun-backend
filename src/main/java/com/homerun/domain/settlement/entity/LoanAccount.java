package com.homerun.domain.settlement.entity;

import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.settlement.type.RepaymentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 실행된 전세대출 계좌(DR-20). 계획당 1건. */
@Entity
@Table(name = "loan_account")
public class LoanAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConsultedLoanProduct product;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CollateralMethod guarantee;

    @Column(nullable = false)
    private Long principal;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal rate;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_type", nullable = false, length = 30)
    private RepaymentType repaymentType;

    @Column(name = "executed_at")
    private LocalDate executedAt;

    @Column(name = "maturity_at")
    private LocalDate maturityAt;

    @Column(name = "preferential_until")
    private LocalDate preferentialUntil;

    @Column(name = "extension_count", nullable = false)
    private int extensionCount;

    protected LoanAccount() {}

    public LoanAccount(Long planId) {
        this.planId = planId;
    }

    /** 등록/수정 시 값 반영. 계획당 1건이라 있으면 덮어쓴다. */
    public void apply(
            ConsultedLoanProduct product,
            CollateralMethod guarantee,
            Long principal,
            BigDecimal rate,
            RepaymentType repaymentType,
            LocalDate executedAt,
            LocalDate maturityAt,
            LocalDate preferentialUntil,
            int extensionCount) {
        this.product = product;
        this.guarantee = guarantee;
        this.principal = principal;
        this.rate = rate;
        this.repaymentType = repaymentType;
        this.executedAt = executedAt;
        this.maturityAt = maturityAt;
        this.preferentialUntil = preferentialUntil;
        this.extensionCount = extensionCount;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public ConsultedLoanProduct getProduct() {
        return product;
    }

    public CollateralMethod getGuarantee() {
        return guarantee;
    }

    public Long getPrincipal() {
        return principal;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public RepaymentType getRepaymentType() {
        return repaymentType;
    }

    public LocalDate getExecutedAt() {
        return executedAt;
    }

    public LocalDate getMaturityAt() {
        return maturityAt;
    }

    public LocalDate getPreferentialUntil() {
        return preferentialUntil;
    }

    public int getExtensionCount() {
        return extensionCount;
    }
}
