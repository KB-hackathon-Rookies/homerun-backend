package com.homerun.domain.dashboard.entity;

import com.homerun.domain.dashboard.type.DeadlineType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "deadline")
public class Deadline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "step_id")
    private Long stepId;

    @Enumerated(EnumType.STRING)
    @Column(name = "deadline_type", nullable = false, length = 20)
    private DeadlineType type;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "is_absolute", nullable = false)
    private boolean absolute;

    protected Deadline() {}

    public Long getStepId() {
        return stepId;
    }

    public DeadlineType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public boolean isAbsolute() {
        return absolute;
    }
}
