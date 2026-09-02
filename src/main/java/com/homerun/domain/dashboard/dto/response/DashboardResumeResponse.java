package com.homerun.domain.dashboard.dto.response;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.type.PlanStage;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "마지막으로 저장한 계획 이어하기 위치")
public record DashboardResumeResponse(
        @Schema(description = "마지막으로 방문한 단계") PlanStage stage,

        @Schema(description = "단계 안에서 마지막으로 저장한 화면 코드", nullable = true)
        String locationCode) {

    public static DashboardResumeResponse from(Plan plan) {
        return new DashboardResumeResponse(plan.getLastVisitedStage(), plan.getLastLocationCode());
    }
}
