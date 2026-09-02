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

    @Column(name = "base_event", nullable = false, length = 50)
    private String baseEvent;

    @Column(name = "base_date")
    private LocalDate baseDate;

    @Column(name = "offset_days", nullable = false)
    private int offsetDays;

    @Column(name = "fact_code", length = 20)
    private String factCode;

    protected Deadline() {}

    private Deadline(
            Long planId,
            Long stepId,
            DeadlineType type,
            String label,
            String baseEvent,
            LocalDate baseDate,
            int offsetDays,
            boolean absolute,
            String factCode) {
        this.planId = planId;
        this.stepId = stepId;
        this.type = type;
        this.label = label;
        this.baseEvent = baseEvent;
        this.baseDate = baseDate;
        this.offsetDays = offsetDays;
        this.dueDate = baseDate.plusDays(offsetDays);
        this.absolute = absolute;
        this.factCode = factCode;
    }

    public static Deadline movePreparation(Long planId, Long stepId, LocalDate targetMoveDate) {
        return new Deadline(
                planId,
                stepId,
                DeadlineType.RECOMMENDED,
                "은행 상담 시작 권장일",
                "TARGET_MOVE_DATE",
                targetMoveDate,
                -21,
                false,
                "FCT-104");
    }

    public Long getId() {
        return id;
    }

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
