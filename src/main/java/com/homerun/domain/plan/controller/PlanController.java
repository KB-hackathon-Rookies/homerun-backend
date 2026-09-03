package com.homerun.domain.plan.controller;

import com.homerun.domain.plan.dto.request.PlanCreateRequest;
import com.homerun.domain.plan.dto.request.UpdatePlanLocationRequest;
import com.homerun.domain.plan.dto.response.PlanProgressResponse;
import com.homerun.domain.plan.dto.response.PlanResponse;
import com.homerun.domain.plan.service.PlanService;
import com.homerun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/plans")
@Tag(name = "계획 진행", description = "독립 계획 생성과 진행 상태 관리")
public class PlanController {

    private final PlanService planService;
    //    @AuthenticationPrincipal MemberPrincipal principal
    @PostMapping
    @Operation(summary = "계획 생성")
    public ResponseEntity<ApiResponse<PlanResponse>> create(
            @RequestParam Long userId, @Valid @RequestBody PlanCreateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(planService.create(userId, request)));
    }

    @GetMapping("/{planId}")
    @Operation(summary = "계획 조회")
    public ApiResponse<PlanResponse> get(@RequestParam Long userId, @PathVariable Long planId) {
        return ApiResponse.success(planService.get(userId, planId));
    }

    @GetMapping("/{planId}/progress")
    @Operation(summary = "계획 진행률 조회")
    public ApiResponse<PlanProgressResponse> getProgress(@RequestParam Long userId, @PathVariable Long planId) {
        return ApiResponse.success(planService.getProgress(userId, planId));
    }

    @PatchMapping("/{planId}/location")
    @Operation(summary = "마지막 위치 저장")
    public ApiResponse<PlanResponse> updateLastLocation(
            @RequestParam Long userId,
            @PathVariable Long planId,
            @Valid @RequestBody UpdatePlanLocationRequest request) {
        return ApiResponse.success(planService.updateLastLocation(userId, planId, request));
    }

    @PostMapping("/{planId}/steps/{stepCode}/tasks/{taskCode}/complete")
    @Operation(summary = "세부 계획 작업 완료")
    public ApiResponse<PlanProgressResponse> completeTask(
            @RequestParam Long userId,
            @PathVariable Long planId,
            @PathVariable String stepCode,
            @PathVariable String taskCode) {
        return ApiResponse.success(planService.completeTask(userId, planId, stepCode, taskCode));
    }

    @PostMapping("/{planId}/reset")
    @Operation(summary = "계획 진행 상태 초기화")
    public ApiResponse<PlanProgressResponse> reset(@RequestParam Long userId, @PathVariable Long planId) {
        return ApiResponse.success(planService.reset(userId, planId));
    }
}
