package com.homerun.domain.contract.entity;

import com.homerun.domain.contract.type.ApplicationMethod;
import com.homerun.domain.contract.type.ContractCollateralMethod;
import com.homerun.domain.contract.type.LoanProductKind;
import com.homerun.domain.plan.type.HouseType;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_product_kind", length = 30)
    private LoanProductKind loanProductKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "collateral_method", length = 30)
    private ContractCollateralMethod collateralMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_method", length = 20)
    private ApplicationMethod applicationMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "house_type", length = 30)
    private HouseType houseType;

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

    /**
     * 2루에서 확정한 매물·은행 상담값으로 3루 계약 초안을 만든다.
     *
     * <p>계약일·잔금일처럼 아직 정할 수 없는 값은 건드리지 않는다. 이 값들을 임의로 채우면
     * 사용자에게 존재하지 않는 계약 일정이 보이기 때문이다.
     */
    public void prefillFromDecision(
            Long propertyId,
            LeaseType leaseType,
            long deposit,
            LoanProductKind loanProductKind,
            ContractCollateralMethod collateralMethod,
            HouseType houseType,
            LocalDate bankConsultedAt) {
        this.propertyId = propertyId;
        this.leaseType = leaseType;
        this.deposit = deposit;
        this.loanProductKind = loanProductKind;
        this.collateralMethod = collateralMethod;
        this.houseType = houseType;
        this.bankConsultedAt = bankConsultedAt;
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
            boolean electronic,
            LoanProductKind loanProductKind,
            ContractCollateralMethod collateralMethod,
            ApplicationMethod applicationMethod,
            HouseType houseType) {
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
        this.loanProductKind = loanProductKind;
        this.collateralMethod = collateralMethod;
        this.applicationMethod = applicationMethod;
        this.houseType = houseType;
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

    public LoanProductKind getLoanProductKind() {
        return loanProductKind;
    }

    public ContractCollateralMethod getCollateralMethod() {
        return collateralMethod;
    }

    public ApplicationMethod getApplicationMethod() {
        return applicationMethod;
    }

    public HouseType getHouseType() {
        return houseType;
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
