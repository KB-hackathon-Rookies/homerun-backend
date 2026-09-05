package com.homerun.domain.diagnosis.controller;

import com.homerun.domain.diagnosis.dto.request.FirstBaseCompleteRequest;
import com.homerun.domain.diagnosis.dto.response.FirstBaseCompleteResponse;
import com.homerun.domain.diagnosis.dto.response.FirstBaseResultResponse;
import com.homerun.domain.diagnosis.service.FirstBaseCompletionService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans/{planId}/first-base")
@Tag(name = "1루 최종 제출", description = "STEP 입력 검증부터 진단 저장과 2루 해제까지 한 번에 처리")
public class FirstBaseController {

    private final FirstBaseCompletionService service;

    public FirstBaseController(FirstBaseCompletionService service) {
        this.service = service;
    }

    @PostMapping("/complete")
    @Operation(summary = "1루 최종 제출", description = "같은 plan_input revision을 다시 제출하면 기존 진단 결과를 반환하며 중복 저장하지 않습니다.")
    public ApiResponse<FirstBaseCompleteResponse> complete(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody FirstBaseCompleteRequest request) {
        return ApiResponse.success(service.complete(principal.memberId(), planId, request));
    }

    @GetMapping("/result")
    @Operation(summary = "1루 완료 결과 조회", description = "완료 당시 정책 판정과 진단 시나리오 스냅샷을 반환합니다.")
    public ApiResponse<FirstBaseResultResponse> result(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.result(principal.memberId(), planId));
    }
}
