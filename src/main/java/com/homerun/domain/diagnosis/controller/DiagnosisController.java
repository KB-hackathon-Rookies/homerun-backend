package com.homerun.domain.diagnosis.controller;

import com.homerun.domain.diagnosis.dto.request.DiagnosisCalculationRequest;
import com.homerun.domain.diagnosis.dto.response.DiagnosisResponse;
import com.homerun.domain.diagnosis.service.DiagnosisService;
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
@RequestMapping("/api/v1/plans/{planId}/diagnosis")
@Tag(name = "1루 독립 가능성 진단", description = "DIA-02 초기 필요자금·월 현금흐름·부족자금과 정책 적용 전후 비교")
public class DiagnosisController {

    private final DiagnosisService service;

    public DiagnosisController(DiagnosisService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "진단 계산 및 저장", description = "RIR 없이 자금과 월 현금흐름으로 가능·주의·어려움을 판정합니다.")
    public ApiResponse<DiagnosisResponse> calculate(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody DiagnosisCalculationRequest request) {
        return ApiResponse.success(service.calculate(principal.memberId(), planId, request));
    }

    @PostMapping("/simulate")
    @Operation(summary = "진단 시뮬레이션", description = "계산 결과를 DB에 저장하지 않습니다.")
    public ApiResponse<DiagnosisResponse> simulate(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody DiagnosisCalculationRequest request) {
        return ApiResponse.success(service.simulate(principal.memberId(), planId, request));
    }

    @GetMapping
    @Operation(summary = "최근 저장 진단 조회")
    public ApiResponse<DiagnosisResponse> latest(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.latest(principal.memberId(), planId));
    }
}
