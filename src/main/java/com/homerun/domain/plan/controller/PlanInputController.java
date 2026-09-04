package com.homerun.domain.plan.controller;

import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.dto.response.PlanInputResponse;
import com.homerun.domain.plan.service.PlanInputService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans/{planId}/input")
@Tag(name = "계획 입력", description = "진단 입력 자동 저장과 모름 필드 관리")
public class PlanInputController {

    private final PlanInputService inputService;

    public PlanInputController(PlanInputService inputService) {
        this.inputService = inputService;
    }

    @PutMapping
    @Operation(
            summary = "계획 입력 저장",
            description =
                    "미완성 상태를 포함한 전체 입력 스냅샷을 멱등하게 생성하거나 갱신합니다. 자동 저장 단계에서는 누락을 허용하고, FIRST_DIAGNOSIS 완료 요청에서 필수 입력을 검사합니다. 확인하기 어려운 값은 값 없이 unknownFields에 필드 코드를 전달합니다.")
    public ApiResponse<PlanInputResponse> save(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody PlanInputRequest request) {
        return ApiResponse.success(inputService.save(principal.memberId(), planId, request));
    }

    @GetMapping
    @Operation(summary = "저장된 계획 입력 조회")
    public ApiResponse<PlanInputResponse> get(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(inputService.get(principal.memberId(), planId));
    }
}
