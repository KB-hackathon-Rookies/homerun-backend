package com.homerun.domain.terms.controller;

import com.homerun.domain.terms.dto.request.RequiredAgreementRequest;
import com.homerun.domain.terms.dto.response.AgreementStatusResponse;
import com.homerun.domain.terms.dto.response.TermResponse;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "필수 약관", description = "현재 유효한 필수 약관과 사용자 동의 상태")
public class TermsController {

    private final TermsService termsService;

    public TermsController(TermsService termsService) {
        this.termsService = termsService;
    }

    @GetMapping("/api/v1/terms")
    @Operation(summary = "필수 약관 조회")
    public ApiResponse<List<TermResponse>> getRequiredTerms() {
        return ApiResponse.success(termsService.getRequiredTerms());
    }

    @GetMapping("/api/v1/agreements/me")
    @Operation(summary = "내 필수 약관 동의 상태 조회")
    public ApiResponse<AgreementStatusResponse> getMyStatus(@AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.success(termsService.getMyStatus(principal.memberId()));
    }

    @PostMapping("/api/v1/agreements")
    @Operation(summary = "필수 약관 일괄 동의")
    public ApiResponse<AgreementStatusResponse> agree(
            @AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody RequiredAgreementRequest request) {
        return ApiResponse.success(termsService.agreeRequiredTerms(principal.memberId(), request));
    }
}
