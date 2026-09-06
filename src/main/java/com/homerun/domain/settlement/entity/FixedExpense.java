package com.homerun.domain.settlement.entity;

import com.homerun.domain.settlement.type.ExpenseCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 고정지출 한 건(DR-20 fixed_expense). */
@Entity
@Table(name = "fixed_expense")
public class FixedExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExpenseCategory category;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "due_day")
    private Integer dueDay;

    @Column(nullable = false)
    private boolean autopay;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected FixedExpense() {}

    public FixedExpense(
            Long userId,
            Long planId,
            String name,
            ExpenseCategory category,
            Long amount,
            Integer dueDay,
            boolean autopay) {
        this.userId = userId;
        this.planId = planId;
        this.name = name;
        this.category = category;
        this.amount = amount;
        this.dueDay = dueDay;
        this.autopay = autopay;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getPlanId() {
        return planId;
    }

    public String getName() {
        return name;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public Long getAmount() {
        return amount;
    }

    public Integer getDueDay() {
        return dueDay;
    }

    public boolean isAutopay() {
        return autopay;
    }

    public boolean isActive() {
        return active;
    }
}
