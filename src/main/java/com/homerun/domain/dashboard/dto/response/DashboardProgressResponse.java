package com.homerun.domain.dashboard.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 진행률은 관문(plan_step)이 아니라 할 일(step_task) 기준이다. 관문은 5개뿐이라 하나
 * 끝낼 때마다 20%씩 튀는데, 사용자가 체감하는 진척과 맞지 않는다(COM-03-08).
 */
@Schema(description = "계획 진행률 요약")
public record DashboardProgressResponse(
        @Schema(description = "DONE 상태인 필수 작업 수") int completedTasks,
        @Schema(description = "필수 작업 수 (선택 작업 제외)") int totalTasks,
        @Schema(description = "진행률", example = "40") int progressPercent) {}
