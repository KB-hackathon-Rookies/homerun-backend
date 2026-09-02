package com.homerun.domain.dashboard.dto.response;

import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.type.PlanStepStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대시보드 할 일")
public record DashboardTaskResponse(
        @Schema(description = "단계 코드", example = "FIRST_DIAGNOSIS")
        String stepCode,

        @Schema(description = "단계 이름", example = "독립 가능성 진단 완료")
        String stepName,

        @Schema(description = "단계 상태") PlanStepStatus status,
        @Schema(description = "단계 순서", example = "2") int sequence) {

    public static DashboardTaskResponse from(PlanStep step) {
        return new DashboardTaskResponse(step.getStepCode(), step.getStepName(), step.getStatus(), step.getSequence());
    }
}
