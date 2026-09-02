package com.homerun.domain.dashboard.controller;

import com.homerun.domain.dashboard.dto.response.DashboardResponse;
import com.homerun.domain.dashboard.service.DashboardService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "계획 대시보드", description = "현재 계획 진행률과 우선 할 일을 요약")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/{planId}/dashboard")
    @Operation(summary = "계획 대시보드 조회")
    public ApiResponse<DashboardResponse> get(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(dashboardService.get(principal.memberId(), planId));
    }
}
