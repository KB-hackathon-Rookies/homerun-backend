package com.homerun.domain.terms.controller;

import com.homerun.domain.terms.dto.request.RequiredAgreementRequest;
import com.homerun.domain.terms.dto.response.AgreementStatusResponse;
import com.homerun.domain.terms.dto.response.TermResponse;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.domain.terms.type.TermScope;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "필수 약관", description = "현재 유효한 필수 약관과 사용자 동의 상태")
public class TermsController {

    private final TermsService termsService;

    public TermsController(TermsService termsService) {
        this.termsService = termsService;
    }

    @GetMapping("/api/v1/terms")
    @Operation(summary = "약관 조회", description = "범위별 약관을 선택 항목까지 함께 돌려줍니다. 생략하면 앱 전체 필수(SERVICE)입니다.")
    public ApiResponse<List<TermResponse>> getTerms(@RequestParam(defaultValue = "SERVICE") TermScope scope) {
        return ApiResponse.success(termsService.getTerms(scope));
    }

    @GetMapping("/api/v1/agreements/me")
    @Operation(summary = "내 약관 동의 상태 조회", description = "범위별로 조회합니다. 생략하면 SERVICE 입니다.")
    public ApiResponse<AgreementStatusResponse> getMyStatus(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(defaultValue = "SERVICE") TermScope scope) {
        return ApiResponse.success(termsService.getMyStatus(principal.memberId(), scope));
    }

    @PostMapping("/api/v1/agreements")
    @Operation(summary = "약관 일괄 동의", description = "범위의 필수 약관을 모두 동의해야 합니다. 선택 항목은 보낸 값 그대로 기록합니다.")
    public ApiResponse<AgreementStatusResponse> agree(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(defaultValue = "SERVICE") TermScope scope,
            @Valid @RequestBody RequiredAgreementRequest request) {
        return ApiResponse.success(termsService.agree(principal.memberId(), scope, request));
    }
}
