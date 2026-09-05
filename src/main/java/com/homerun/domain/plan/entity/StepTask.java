package com.homerun.domain.plan.entity;

import com.homerun.domain.plan.type.StepTaskStatus;
import com.homerun.domain.plan.type.StepTaskTemplate;
import com.homerun.domain.plan.type.TaskRecurrence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 관문(plan_step) 안에서 사용자가 실제로 수행하는 할 일.
 *
 * <p>대시보드의 할 일 목록은 plan_step 이 아니라 이 엔티티를 읽는다. "3루 실행"은
 * 사용자에게 줄 지시가 아니고 "전입신고"는 지시이기 때문이다.
 */
@Entity
@Table(name = "step_task")
public class StepTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_step_id", nullable = false)
    private Long planStepId;

    @Column(name = "task_code", nullable = false, length = 50)
    private String taskCode;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    @Column(nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StepTaskStatus status;

    @Column(name = "is_irreversible", nullable = false)
    private boolean irreversible;

    @Column(name = "due_at")
    private LocalDate dueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type", nullable = false, length = 20)
    private TaskRecurrence recurrence;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected StepTask() {}

    private StepTask(Long planStepId, StepTaskTemplate template) {
        this.planStepId = planStepId;
        this.taskCode = template.code();
        this.taskName = template.displayName();
        this.sequence = template.sequence();
        this.irreversible = template.irreversible();
        this.recurrence = template.recurrence();
        this.status = StepTaskStatus.TODO;
        this.updatedAt = Instant.now();
    }

    public static StepTask of(Long planStepId, StepTaskTemplate template) {
        return new StepTask(planStepId, template);
    }

    public void start() {
        if (status == StepTaskStatus.TODO || status == StepTaskStatus.RECALC_REQUIRED) {
            status = StepTaskStatus.DOING;
            updatedAt = Instant.now();
        }
    }

    public void complete() {
        status = StepTaskStatus.DONE;
        completedAt = Instant.now();
        updatedAt = completedAt;
    }

    public void skip() {
        status = StepTaskStatus.SKIPPED;
        completedAt = null;
        updatedAt = Instant.now();
    }

    /** COM-03-07. 선행 입력이 바뀌면 이미 끝낸 할 일도 다시 하도록 되돌린다. */
    public void requireRecalculation() {
        if (status == StepTaskStatus.DONE || status == StepTaskStatus.DOING) {
            status = StepTaskStatus.RECALC_REQUIRED;
            completedAt = null;
            updatedAt = Instant.now();
        }
    }

    public void reset() {
        status = StepTaskStatus.TODO;
        completedAt = null;
        updatedAt = Instant.now();
    }

    /** 홈 서비스가 실제 작업 인스턴스를 만들 때 마감과 반복 주기를 지정한다. */
    public void schedule(LocalDate dueAt, TaskRecurrence recurrence) {
        this.dueAt = dueAt;
        this.recurrence = recurrence;
        this.updatedAt = Instant.now();
    }

    /** 마감이 지난 미완료 작업만 만료시킨다. 완료·건너뜀 작업은 이력을 보존한다. */
    public boolean expireIfOverdue(LocalDate today) {
        if (dueAt == null || !today.isAfter(dueAt) || isSettled() || status == StepTaskStatus.EXPIRED) {
            return false;
        }
        status = StepTaskStatus.EXPIRED;
        completedAt = null;
        updatedAt = Instant.now();
        return true;
    }

    /** 대시보드에 노출할 대상인지. 끝났거나 건너뛴 것은 할 일이 아니다. */
    public boolean isActionable() {
        return status == StepTaskStatus.TODO
                || status == StepTaskStatus.DOING
                || status == StepTaskStatus.RECALC_REQUIRED;
    }

    public boolean isSettled() {
        return status == StepTaskStatus.DONE || status == StepTaskStatus.SKIPPED;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanStepId() {
        return planStepId;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public String getTaskName() {
        return taskName;
    }

    public int getSequence() {
        return sequence;
    }

    public StepTaskStatus getStatus() {
        return status;
    }

    public boolean isIrreversible() {
        return irreversible;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public LocalDate getDueAt() {
        return dueAt;
    }

    public TaskRecurrence getRecurrence() {
        return recurrence;
    }
}
