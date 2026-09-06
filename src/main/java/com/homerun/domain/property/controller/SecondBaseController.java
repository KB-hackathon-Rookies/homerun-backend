package com.homerun.domain.property.controller;

import com.homerun.domain.property.dto.request.SecondBaseCompleteRequest;
import com.homerun.domain.property.dto.response.SecondBaseCompleteResponse;
import com.homerun.domain.property.dto.response.SecondBaseResultResponse;
import com.homerun.domain.property.service.SecondBaseCompletionService;
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
@RequestMapping("/api/v1/plans/{planId}/second-base")
@Tag(name = "2루 최종 제출", description = "최종 매물·상담 결과를 확정하고 3루를 개방")
public class SecondBaseController {

    private final SecondBaseCompletionService service;

    public SecondBaseController(SecondBaseCompletionService service) {
        this.service = service;
    }

    @PostMapping("/complete")
    @Operation(summary = "2루 최종 제출", description = "같은 결정 revision을 다시 제출하면 기존 완료 결과를 반환합니다.")
    public ApiResponse<SecondBaseCompleteResponse> complete(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody SecondBaseCompleteRequest request) {
        return ApiResponse.success(service.complete(principal.memberId(), planId, request));
    }

    @GetMapping("/result")
    @Operation(summary = "2루 완료 결과 조회", description = "완료 당시 최종 매물과 은행 상담 조건을 반환합니다.")
    public ApiResponse<SecondBaseResultResponse> result(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.result(principal.memberId(), planId));
    }
}
