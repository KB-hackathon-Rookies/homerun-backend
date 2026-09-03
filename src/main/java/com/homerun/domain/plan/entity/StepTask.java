package com.homerun.domain.plan.entity;

import com.homerun.domain.plan.type.StepTaskStatus;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "step_task",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_step_task_sequence",
                    columnNames = {"plan_step_id", "sequence"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StepTaskStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static StepTask create(Long planStepId, String taskCode, String taskName, int sequence) {
        StepTask task = new StepTask();

        task.planStepId = planStepId;
        task.taskCode = taskCode;
        task.taskName = taskName;
        task.sequence = sequence;
        task.status = StepTaskStatus.TODO;
        task.createdAt = Instant.now();
        task.updatedAt = Instant.now();

        return task;
    }

    public void complete() {
        this.status = StepTaskStatus.DONE;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void reset() {
        this.status = StepTaskStatus.TODO;
        this.completedAt = null;
        this.updatedAt = Instant.now();
    }
}
