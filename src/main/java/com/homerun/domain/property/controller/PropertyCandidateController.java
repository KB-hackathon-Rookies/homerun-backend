package com.homerun.domain.property.controller;

import com.homerun.domain.property.dto.request.PropertyCandidateAnalysisRequest;
import com.homerun.domain.property.dto.response.PropertyCandidateAnalysisResponse;
import com.homerun.domain.property.dto.response.PropertyCandidateResponse;
import com.homerun.domain.property.service.PropertyCandidateService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans/{planId}/properties")
@Tag(name = "전세 매물 후보", description = "최대 3개 매물의 외부 조회·안전성 판정·최종 선택")
public class PropertyCandidateController {

    private final PropertyCandidateService service;

    public PropertyCandidateController(PropertyCandidateService service) {
        this.service = service;
    }

    @PostMapping("/analysis")
    @Operation(summary = "매물 후보 통합 분석", description = "건축물대장과 전월세 실거래가를 조회하고 등기 확인값을 합쳐 판정합니다.")
    public ApiResponse<PropertyCandidateAnalysisResponse> analyze(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody PropertyCandidateAnalysisRequest request) {
        return ApiResponse.success(service.analyzeAndSave(principal.memberId(), planId, request));
    }

    @GetMapping
    @Operation(summary = "매물 후보 목록 조회")
    public ApiResponse<List<PropertyCandidateResponse>> candidates(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.getCandidates(principal.memberId(), planId));
    }

    @PutMapping("/{propertyId}/selection")
    @Operation(summary = "최종 매물 선택", description = "기존 선택을 해제하고 지정한 매물 하나를 최종 선택합니다.")
    public ApiResponse<PropertyCandidateResponse> select(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId) {
        return ApiResponse.success(service.select(principal.memberId(), planId, propertyId));
    }
}
