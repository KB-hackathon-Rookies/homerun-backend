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

    // 마감은 관문이 아니라 할 일에 붙는다. 대시보드는 이 값으로만 마감을 찾는다.
    @Column(name = "task_id")
    private Long taskId;

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
            Long taskId,
            DeadlineType type,
            String label,
            String baseEvent,
            LocalDate baseDate,
            int offsetDays,
            boolean absolute,
            String factCode) {
        this.planId = planId;
        this.taskId = taskId;
        this.type = type;
        this.label = label;
        this.baseEvent = baseEvent;
        this.baseDate = baseDate;
        this.offsetDays = offsetDays;
        this.dueDate = baseDate.plusDays(offsetDays);
        this.absolute = absolute;
        this.factCode = factCode;
    }

    /**
     * 계약 날짜에서 역산한 마감(SEQ-01-04).
     *
     * <p>{@code movePreparation} 은 계획을 만들 때 희망 이사일로 잡는 어림값이다. 계약이
     * 맺어져 잔금일이 정해지면 그 날짜로 다시 만든다.
     *
     * @param factCode 근거 기준. 다시 계산할 때 이 코드로 옛 마감을 찾아 지운다
     */
    public static Deadline derived(
            Long planId,
            Long taskId,
            DeadlineType type,
            String label,
            String baseEvent,
            LocalDate baseDate,
            int offsetDays,
            String factCode) {
        return new Deadline(planId, taskId, type, label, baseEvent, baseDate, offsetDays, false, factCode);
    }

    public static Deadline movePreparation(Long planId, Long taskId, LocalDate targetMoveDate) {
        return new Deadline(
                planId,
                taskId,
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

    public Long getTaskId() {
        return taskId;
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

    public String getBaseEvent() {
        return baseEvent;
    }

    public LocalDate getBaseDate() {
        return baseDate;
    }

    public int getOffsetDays() {
        return offsetDays;
    }

    /** 어떤 기준에서 나온 마감인지. 다시 계산할 때 이 코드로 옛 것을 찾는다. */
    public String getFactCode() {
        return factCode;
    }
}
