package com.homerun.domain.dashboard.dto.response;

import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.plan.type.PlanStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "계획 대시보드 요약")
public record DashboardResponse(
        @Schema(description = "계획 ID") Long planId,
        @Schema(description = "임대 유형") LeaseType leaseType,
        @Schema(description = "현재 계획 단계") PlanStage currentStage,
        @Schema(description = "계획 상태") PlanStatus planStatus,
        @Schema(description = "마지막으로 머문 위치 코드") String lastLocationCode,
        @Schema(description = "목표 이사일") LocalDate targetMoveDate,
        @Schema(description = "계획 진행률") DashboardProgressResponse progress,
        @Schema(description = "우선순위가 적용된 할 일") List<DashboardTaskResponse> prioritizedTasks) {

    public DashboardResponse {
        prioritizedTasks = List.copyOf(prioritizedTasks);
    }
}
