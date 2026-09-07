package com.homerun.domain.settlement.controller;

import com.homerun.domain.settlement.dto.request.ReturnGuaranteeEnrollmentRequest;
import com.homerun.domain.settlement.dto.response.ReturnGuaranteeEnrollmentResponse;
import com.homerun.domain.settlement.service.ReturnGuaranteeEnrollmentService;
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
@RequestMapping("/api/v1/plans/{planId}/return-guarantee")
@Tag(name = "반환보증 가입", description = "반환보증 가입 완료·보증료 납부 상태 저장(FR-H1-03)")
public class ReturnGuaranteeEnrollmentController {

    private final ReturnGuaranteeEnrollmentService service;

    public ReturnGuaranteeEnrollmentController(ReturnGuaranteeEnrollmentService service) {
        this.service = service;
    }

    @PutMapping("/enrollment")
    @Operation(
            summary = "반환보증 가입 상태 저장",
            description = "가입 완료·보증료 납부 완료를 계획당 1건 저장한다. 가입·납부가 끝나야 보증료 지원(4-2)"
                    + " 신청이 활성화된다(feeSupportApplicable). 다시 저장하면 덮어쓴다.")
    public ApiResponse<ReturnGuaranteeEnrollmentResponse> save(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody ReturnGuaranteeEnrollmentRequest request) {
        return ApiResponse.success(service.save(principal.memberId(), planId, request));
    }

    @GetMapping("/enrollment")
    @Operation(summary = "반환보증 가입 상태 조회")
    public ApiResponse<ReturnGuaranteeEnrollmentResponse> get(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.get(principal.memberId(), planId));
    }
}
