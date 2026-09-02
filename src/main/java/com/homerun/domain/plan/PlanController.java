//package com.homerun.domain.plan;
//
//import com.homerun.global.response.ApiResponse;
//import com.homerun.global.security.MemberPrincipal;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import jakarta.validation.Valid;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.annotation.AuthenticationPrincipal;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PatchMapping;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@RequestMapping("/api/v1/plans")
//@Tag(name = "계획 진행", description = "독립 계획 생성과 진행 상태 관리")
//public class PlanController {
//
//    private final PlanService planService;
//
//    public PlanController(PlanService planService) {
//        this.planService = planService;
//    }
//
//    @PostMapping
//    @Operation(summary = "계획 생성")
//    public ResponseEntity<ApiResponse<PlanResponse>> create(
//            @AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody CreatePlanRequest request) {
//        return ResponseEntity.status(HttpStatus.CREATED)
//                .body(ApiResponse.success(planService.create(principal.memberId(), request)));
//    }
//
//    @GetMapping("/{planId}")
//    @Operation(summary = "계획 조회")
//    public ApiResponse<PlanResponse> get(
//            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
//        return ApiResponse.success(planService.get(principal.memberId(), planId));
//    }
//
//    @GetMapping("/{planId}/progress")
//    @Operation(summary = "계획 진행률 조회")
//    public ApiResponse<PlanProgressResponse> getProgress(
//            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
//        return ApiResponse.success(planService.getProgress(principal.memberId(), planId));
//    }
//
//    @PatchMapping("/{planId}/location")
//    @Operation(summary = "마지막 위치 저장")
//    public ApiResponse<PlanResponse> updateLastLocation(
//            @AuthenticationPrincipal MemberPrincipal principal,
//            @PathVariable Long planId,
//            @Valid @RequestBody UpdatePlanLocationRequest request) {
//        return ApiResponse.success(planService.updateLastLocation(principal.memberId(), planId, request));
//    }
//
//    @PostMapping("/{planId}/steps/{stepCode}/complete")
//    @Operation(summary = "계획 단계 완료")
//    public ApiResponse<PlanProgressResponse> completeStep(
//            @AuthenticationPrincipal MemberPrincipal principal,
//            @PathVariable Long planId,
//            @PathVariable String stepCode,
//            @Valid @RequestBody CompletePlanStepRequest request) {
//        return ApiResponse.success(planService.completeStep(principal.memberId(), planId, stepCode, request));
//    }
//
//    @PostMapping("/{planId}/reset")
//    @Operation(summary = "계획 진행 상태 초기화")
//    public ApiResponse<PlanProgressResponse> reset(
//            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
//        return ApiResponse.success(planService.reset(principal.memberId(), planId));
//    }
//}
