package com.homerun.domain.dashboard.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "계획 진행률 요약")
public record DashboardProgressResponse(
        @Schema(description = "완료한 단계 수") int completedSteps,
        @Schema(description = "전체 단계 수") int totalSteps,
        @Schema(description = "진행률", example = "40") int progressPercent) {}
