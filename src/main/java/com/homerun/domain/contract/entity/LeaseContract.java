package com.homerun.domain.contract.entity;

import com.homerun.domain.plan.type.LeaseType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 임대차 계약 한 건(PRP-02).
 *
 * <p>날짜 컬럼이 두 종류다. {@code balanceDate}·{@code moveInDate} 는 예정일이고,
 * {@code balancePaidAt}·{@code moveInReportAt} 은 실제로 끝난 날이다. 진행상태는 뒤쪽만
 * 보고 판단한다(V12).
 */
@Entity
@Table(name = "lease_contract")
public class LeaseContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "property_id")
    private Long propertyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "lease_type", nullable = false, length = 20)
    private LeaseType leaseType;

    @Column(nullable = false)
    private long deposit;

    @Column(name = "monthly_rent", nullable = false)
    private long monthlyRent;

    @Column(name = "maintenance_fee", nullable = false)
    private long maintenanceFee;

    @Column(name = "down_payment")
    private Long downPayment;

    @Column(name = "contract_date")
    private LocalDate contractDate;

    @Column(name = "balance_date")
    private LocalDate balanceDate;

    @Column(name = "move_in_date")
    private LocalDate moveInDate;

    @Column(name = "confirmed_date_at")
    private LocalDate confirmedDateAt;

    @Column(name = "move_in_report_at")
    private LocalDate moveInReportAt;

    @Column(name = "bank_consulted_at")
    private LocalDate bankConsultedAt;

    @Column(name = "loan_applied_at")
    private LocalDate loanAppliedAt;

    @Column(name = "balance_paid_at")
    private LocalDate balancePaidAt;

    @Column(name = "is_electronic", nullable = false)
    private boolean electronic;

    @Column(name = "lease_end_date")
    private LocalDate leaseEndDate;

    @Column(name = "renewal_notified_at")
    private LocalDate renewalNotifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected LeaseContract() {}

    public LeaseContract(Long planId, LeaseType leaseType, long deposit, long monthlyRent) {
        this.planId = planId;
        this.leaseType = leaseType;
        this.deposit = deposit;
        this.monthlyRent = monthlyRent;
    }

    /** 계약 정보를 통째로 덮어쓴다. 입력 화면이 전체 폼을 그대로 보내는 구조다. */
    public void overwrite(
            Long propertyId,
            LeaseType leaseType,
            long deposit,
            long monthlyRent,
            long maintenanceFee,
            Long downPayment,
            LocalDate contractDate,
            LocalDate balanceDate,
            LocalDate moveInDate,
            LocalDate confirmedDateAt,
            LocalDate moveInReportAt,
            LocalDate bankConsultedAt,
            LocalDate loanAppliedAt,
            LocalDate balancePaidAt,
            boolean electronic) {
        this.propertyId = propertyId;
        this.leaseType = leaseType;
        this.deposit = deposit;
        this.monthlyRent = monthlyRent;
        this.maintenanceFee = maintenanceFee;
        this.downPayment = downPayment;
        this.contractDate = contractDate;
        this.balanceDate = balanceDate;
        this.moveInDate = moveInDate;
        this.confirmedDateAt = confirmedDateAt;
        this.moveInReportAt = moveInReportAt;
        this.bankConsultedAt = bankConsultedAt;
        this.loanAppliedAt = loanAppliedAt;
        this.balancePaidAt = balancePaidAt;
        this.electronic = electronic;
    }

    /** 보증금이 걸린 계약인가. 순수 월세가 아니면 전세와 같은 검증이 필요하다(PRP-02-07). */
    public boolean hasDeposit() {
        return deposit > 0;
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

    public LeaseType getLeaseType() {
        return leaseType;
    }

    public long getDeposit() {
        return deposit;
    }

    public long getMonthlyRent() {
        return monthlyRent;
    }

    public long getMaintenanceFee() {
        return maintenanceFee;
    }

    public Long getDownPayment() {
        return downPayment;
    }

    public LocalDate getContractDate() {
        return contractDate;
    }

    public LocalDate getBalanceDate() {
        return balanceDate;
    }

    public LocalDate getMoveInDate() {
        return moveInDate;
    }

    public LocalDate getConfirmedDateAt() {
        return confirmedDateAt;
    }

    public LocalDate getMoveInReportAt() {
        return moveInReportAt;
    }

    public LocalDate getBankConsultedAt() {
        return bankConsultedAt;
    }

    public LocalDate getLoanAppliedAt() {
        return loanAppliedAt;
    }

    public LocalDate getBalancePaidAt() {
        return balancePaidAt;
    }

    public boolean isElectronic() {
        return electronic;
    }

    public LocalDate getLeaseEndDate() {
        return leaseEndDate;
    }

    public LocalDate getRenewalNotifiedAt() {
        return renewalNotifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
