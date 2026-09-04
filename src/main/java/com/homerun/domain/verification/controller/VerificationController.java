package com.homerun.domain.verification.controller;

import com.homerun.domain.verification.dto.response.VerificationDtos.PendingConditionList;
import com.homerun.domain.verification.service.VerificationService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 추가 확인 관리(VER-01). */
@RestController
@RequestMapping("/api/v1/plans/{planId}/verifications")
@Tag(name = "추가 확인 관리", description = "정책 판정에서 추가 확인으로 빠진 조건 조회")
public class VerificationController {

    private final VerificationService service;

    public VerificationController(VerificationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "미확인 조건 조회", description = "계획에 걸린 정책 판정 중 추가 확인(NEED_INFO)으로 빠진 조건을 모아 확인 방법과 함께 반환한다.")
    public ApiResponse<PendingConditionList> listPending(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.listPending(principal.memberId(), planId));
    }
}
