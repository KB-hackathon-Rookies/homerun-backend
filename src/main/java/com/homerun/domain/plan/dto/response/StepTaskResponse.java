package com.homerun.domain.plan.dto.response;

import com.homerun.domain.plan.entity.StepTask;
import com.homerun.domain.plan.type.StepTaskStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StepTaskResponse {

    private Long id;

    private String taskCode;

    private String taskName;

    private Integer sequence;

    private StepTaskStatus status;

    private Instant completedAt;

    public static StepTaskResponse from(StepTask task) {

        return StepTaskResponse.builder()
                .id(task.getId())
                .taskCode(task.getTaskCode())
                .taskName(task.getTaskName())
                .sequence(task.getSequence())
                .status(task.getStatus())
                .completedAt(task.getCompletedAt())
                .build();
    }
}
