package com.homerun.domain.openbanking.entity;

import com.homerun.domain.openbanking.type.FinancialSnapshotSource;
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

@Entity
@Table(name = "financial_snapshot")
public class FinancialSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FinancialSnapshotSource source;

    @Column(name = "confirmed_by_user", nullable = false)
    private boolean confirmedByUser;

    @Column(name = "financial_asset")
    private Long financialAsset;

    @Column(name = "monthly_income")
    private Long monthlyIncome;

    @Column(name = "monthly_expense")
    private Long monthlyExpense;

    @Column(name = "loan_balance")
    private Long loanBalance;

    @Column(name = "monthly_debt_payment")
    private Long monthlyDebtPayment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FinancialSnapshot() {}

    private FinancialSnapshot(
            Long userId,
            LocalDate asOf,
            Long financialAsset,
            Long monthlyIncome,
            Long monthlyDebtPayment,
            Instant createdAt) {
        this.userId = userId;
        this.asOf = asOf;
        this.source = FinancialSnapshotSource.OPEN_BANKING;
        this.confirmedByUser = false;
        this.financialAsset = financialAsset;
        this.monthlyIncome = monthlyIncome;
        this.monthlyDebtPayment = monthlyDebtPayment;
        this.createdAt = createdAt;
    }

    public static FinancialSnapshot fromOpenBanking(
            Long userId,
            LocalDate asOf,
            Long financialAsset,
            Long monthlyIncome,
            Long monthlyDebtPayment,
            Instant createdAt) {
        return new FinancialSnapshot(userId, asOf, financialAsset, monthlyIncome, monthlyDebtPayment, createdAt);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getAsOf() {
        return asOf;
    }

    public FinancialSnapshotSource getSource() {
        return source;
    }

    public boolean isConfirmedByUser() {
        return confirmedByUser;
    }

    public Long getFinancialAsset() {
        return financialAsset;
    }

    public Long getMonthlyIncome() {
        return monthlyIncome;
    }

    public Long getMonthlyExpense() {
        return monthlyExpense;
    }

    public Long getLoanBalance() {
        return loanBalance;
    }

    public Long getMonthlyDebtPayment() {
        return monthlyDebtPayment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
