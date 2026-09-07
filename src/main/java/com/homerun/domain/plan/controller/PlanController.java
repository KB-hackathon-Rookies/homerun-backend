package com.homerun.domain.plan.controller;

import com.homerun.domain.plan.dto.request.CompletePlanStepRequest;
import com.homerun.domain.plan.dto.request.CreatePlanRequest;
import com.homerun.domain.plan.dto.request.UpdatePlanLocationRequest;
import com.homerun.domain.plan.dto.response.PlanProgressResponse;
import com.homerun.domain.plan.dto.response.PlanResponse;
import com.homerun.domain.plan.service.PlanService;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "계획 진행", description = "독립 계획 생성과 진행 상태 관리")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    @Operation(summary = "계획 생성")
    public ResponseEntity<ApiResponse<PlanResponse>> create(
            @AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody CreatePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(planService.create(principal.memberId(), request)));
    }

    @GetMapping
    @Operation(summary = "내 계획 목록 조회", description = "로그인 사용자의 계획을 최근 활동 순으로 조회합니다.")
    public ApiResponse<List<PlanResponse>> getAll(@AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.success(planService.getAll(principal.memberId()));
    }

    @GetMapping("/active")
    @Operation(summary = "진행 중인 계획 이어하기", description = "가장 최근에 활동한 ACTIVE 계획과 마지막 위치를 조회합니다.")
    public ApiResponse<PlanResponse> getActive(@AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.success(planService.getActive(principal.memberId()));
    }

    @GetMapping("/{planId}")
    @Operation(summary = "계획 조회")
    public ApiResponse<PlanResponse> get(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(planService.get(principal.memberId(), planId));
    }

    @GetMapping("/{planId}/progress")
    @Operation(summary = "계획 진행률 조회")
    public ApiResponse<PlanProgressResponse> getProgress(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(planService.getProgress(principal.memberId(), planId));
    }

    @PatchMapping("/{planId}/location")
    @Operation(summary = "마지막 위치 저장")
    public ApiResponse<PlanResponse> updateLastLocation(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody UpdatePlanLocationRequest request) {
        return ApiResponse.success(planService.updateLastLocation(principal.memberId(), planId, request));
    }

    @PostMapping("/{planId}/stages/{targetStage}/enter")
    @Operation(summary = "현재 또는 완료한 이전 단계 진입")
    public ApiResponse<PlanResponse> enterStage(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable PlanStage targetStage,
            @Valid @RequestBody UpdatePlanLocationRequest request) {
        return ApiResponse.success(planService.enterStage(principal.memberId(), planId, targetStage, request));
    }

    @PostMapping("/{planId}/steps/{stepCode}/complete")
    @Operation(
            summary = "계획 단계 완료",
            description =
                    "FIRST_DIAGNOSIS 단계는 모든 진단 입력이 저장되어 있어야 완료할 수 있습니다. 확인하기 어려운 값은 입력 저장 API의 unknownFields로 모름 처리할 수 있으며, 누락된 값은 PLAN_013의 fieldErrors로 한 번에 반환합니다.")
    public ApiResponse<PlanProgressResponse> completeStep(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable String stepCode,
            @Valid @RequestBody CompletePlanStepRequest request) {
        return ApiResponse.success(planService.completeStep(principal.memberId(), planId, stepCode, request));
    }

    @PostMapping("/{planId}/reset")
    @Operation(summary = "계획 진행 상태 초기화")
    public ApiResponse<PlanProgressResponse> reset(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(planService.reset(principal.memberId(), planId));
    }
}
