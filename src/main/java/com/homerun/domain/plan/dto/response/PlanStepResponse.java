package com.homerun.domain.plan.dto.response;

import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.type.PlanStepStatus;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlanStepResponse {

    private Long id;

    private String stepCode;

    private String stepName;

    private Integer stepGroup;

    private PlanStepStatus status;

    private Boolean irreversible;

    private Instant completedAt;

    private List<StepTaskResponse> tasks;

    public static PlanStepResponse from(PlanStep step, List<StepTaskResponse> tasks) {

        return PlanStepResponse.builder()
                .id(step.getId())
                .stepCode(step.getStepCode())
                .stepName(step.getStepName())
                .stepGroup(step.getStepGroup())
                .status(step.getStatus())
                .irreversible(step.isIrreversible())
                .completedAt(step.getCompletedAt())
                .tasks(tasks)
                .build();
    }
}
