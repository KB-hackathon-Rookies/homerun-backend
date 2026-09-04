package com.homerun.domain.plan.controller;

import com.homerun.domain.plan.dto.request.UpdateStepTaskStatusRequest;
import com.homerun.domain.plan.dto.response.PlanTaskProgressResponse;
import com.homerun.domain.plan.service.PlanTaskService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans/{planId}/tasks")
@Tag(name = "계획 할 일", description = "계획 단계별 개별 할 일 진행 상태 관리")
public class PlanTaskController {

    private final PlanTaskService planTaskService;

    public PlanTaskController(PlanTaskService planTaskService) {
        this.planTaskService = planTaskService;
    }

    @GetMapping
    @Operation(summary = "계획 할 일 목록과 진행률 조회")
    public ApiResponse<PlanTaskProgressResponse> getTasks(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(planTaskService.getTasks(principal.memberId(), planId));
    }

    @PatchMapping("/{taskCode}")
    @Operation(summary = "개별 할 일 상태 변경", description = "DOING, DONE 또는 건너뛰기 가능한 할 일의 SKIPPED 상태로 변경합니다.")
    public ApiResponse<PlanTaskProgressResponse> updateStatus(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable String taskCode,
            @Valid @RequestBody UpdateStepTaskStatusRequest request) {
        return ApiResponse.success(planTaskService.updateStatus(principal.memberId(), planId, taskCode, request));
    }
}
