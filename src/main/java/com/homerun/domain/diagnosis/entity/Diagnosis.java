package com.homerun.domain.diagnosis.entity;

import com.homerun.domain.diagnosis.type.DiagnosisVerdict;
import com.homerun.domain.diagnosis.type.DiagnosisWarning;
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
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "diagnosis")
public class Diagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "cost_estimate_id")
    private Long costEstimateId;

    @Column(name = "available_cash", nullable = false)
    private long availableCash;

    @Column(name = "returnable_deposit", nullable = false)
    private long returnableDeposit;

    @Column(name = "monthly_income", nullable = false)
    private long monthlyIncome;

    @Column(name = "monthly_housing_cost", nullable = false)
    private long monthlyHousingCost;

    @Column(name = "monthly_living_expense", nullable = false)
    private long monthlyLivingExpense;

    @Column(name = "monthly_debt_payment", nullable = false)
    private long monthlyDebtPayment;

    @Column(name = "monthly_disposable", nullable = false)
    private long monthlyDisposable;

    @Column(name = "months_to_move", nullable = false)
    private int monthsToMove;

    @Column(name = "savable_amount", nullable = false)
    private long savableAmount;

    @Column(name = "expected_fund", nullable = false)
    private long expectedFund;

    @Column(name = "expected_loan_amount", nullable = false)
    private long expectedLoanAmount;

    @Column(name = "expected_monthly_interest", nullable = false)
    private long expectedMonthlyInterest;

    @Column(name = "required_cash_after_policy", nullable = false)
    private long requiredCashAfterPolicy;

    @Column(nullable = false)
    private long shortfall;

    @Column(name = "possible_date_before_policy")
    private LocalDate possibleDateBeforePolicy;

    @Column(name = "possible_date")
    private LocalDate possibleDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiagnosisVerdict verdict;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<DiagnosisWarning> warnings;

    @Column(name = "engine_version", nullable = false, length = 20)
    private String engineVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Diagnosis() {}

    public Diagnosis(
            Long planId,
            Long costEstimateId,
            long availableCash,
            long returnableDeposit,
            long monthlyIncome,
            long monthlyHousingCost,
            long monthlyLivingExpense,
            long monthlyDebtPayment,
            long monthlyDisposable,
            int monthsToMove,
            long savableAmount,
            long expectedFund,
            long expectedLoanAmount,
            long expectedMonthlyInterest,
            long requiredCashAfterPolicy,
            long shortfall,
            LocalDate possibleDateBeforePolicy,
            LocalDate possibleDate,
            DiagnosisVerdict verdict,
            List<DiagnosisWarning> warnings,
            String engineVersion,
            Instant createdAt) {
        this.planId = planId;
        this.costEstimateId = costEstimateId;
        this.availableCash = availableCash;
        this.returnableDeposit = returnableDeposit;
        this.monthlyIncome = monthlyIncome;
        this.monthlyHousingCost = monthlyHousingCost;
        this.monthlyLivingExpense = monthlyLivingExpense;
        this.monthlyDebtPayment = monthlyDebtPayment;
        this.monthlyDisposable = monthlyDisposable;
        this.monthsToMove = monthsToMove;
        this.savableAmount = savableAmount;
        this.expectedFund = expectedFund;
        this.expectedLoanAmount = expectedLoanAmount;
        this.expectedMonthlyInterest = expectedMonthlyInterest;
        this.requiredCashAfterPolicy = requiredCashAfterPolicy;
        this.shortfall = shortfall;
        this.possibleDateBeforePolicy = possibleDateBeforePolicy;
        this.possibleDate = possibleDate;
        this.verdict = verdict;
        this.warnings = List.copyOf(warnings);
        this.engineVersion = engineVersion;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public Long getCostEstimateId() {
        return costEstimateId;
    }

    public long getAvailableCash() {
        return availableCash;
    }

    public long getReturnableDeposit() {
        return returnableDeposit;
    }

    public long getMonthlyIncome() {
        return monthlyIncome;
    }

    public long getMonthlyHousingCost() {
        return monthlyHousingCost;
    }

    public long getMonthlyLivingExpense() {
        return monthlyLivingExpense;
    }

    public long getMonthlyDebtPayment() {
        return monthlyDebtPayment;
    }

    public long getMonthlyDisposable() {
        return monthlyDisposable;
    }

    public int getMonthsToMove() {
        return monthsToMove;
    }

    public long getSavableAmount() {
        return savableAmount;
    }

    public long getExpectedFund() {
        return expectedFund;
    }

    public long getExpectedLoanAmount() {
        return expectedLoanAmount;
    }

    public long getExpectedMonthlyInterest() {
        return expectedMonthlyInterest;
    }

    public long getRequiredCashAfterPolicy() {
        return requiredCashAfterPolicy;
    }

    public long getShortfall() {
        return shortfall;
    }

    public LocalDate getPossibleDateBeforePolicy() {
        return possibleDateBeforePolicy;
    }

    public LocalDate getPossibleDate() {
        return possibleDate;
    }

    public DiagnosisVerdict getVerdict() {
        return verdict;
    }

    public List<DiagnosisWarning> getWarnings() {
        return warnings;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
