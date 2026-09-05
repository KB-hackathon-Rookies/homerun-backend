package com.homerun.domain.property.controller;

import com.homerun.domain.property.dto.request.BankConsultationRequest;
import com.homerun.domain.property.dto.request.PropertyCandidateAnalysisRequest;
import com.homerun.domain.property.dto.request.PropertyComparisonRequest;
import com.homerun.domain.property.dto.request.PropertyDecisionRequest;
import com.homerun.domain.property.dto.response.BankConsultationResponse;
import com.homerun.domain.property.dto.response.PropertyCandidateAnalysisResponse;
import com.homerun.domain.property.dto.response.PropertyCandidateResponse;
import com.homerun.domain.property.dto.response.PropertyComparisonResponse;
import com.homerun.domain.property.dto.response.PropertyDecisionResponse;
import com.homerun.domain.property.dto.response.PropertyPolicyVerdictListResponse;
import com.homerun.domain.property.service.PropertyCandidateService;
import com.homerun.domain.property.service.PropertyDecisionService;
import com.homerun.domain.property.service.PropertyPolicyVerdictService;
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
@Tag(name = "전세 매물 후보", description = "매물 후보의 외부 조회·안전성 판정·비교")
public class PropertyCandidateController {

    private final PropertyCandidateService service;
    private final PropertyDecisionService decisionService;
    private final PropertyPolicyVerdictService policyVerdictService;

    public PropertyCandidateController(
            PropertyCandidateService service,
            PropertyDecisionService decisionService,
            PropertyPolicyVerdictService policyVerdictService) {
        this.service = service;
        this.decisionService = decisionService;
        this.policyVerdictService = policyVerdictService;
    }

    @PostMapping("/{propertyId}/policy-verdicts")
    @Operation(
            summary = "매물 x 상품 판정",
            description = "이 매물로 전세 상품마다 되는지 판정하고 매물별로 남긴다(DR-10 · BR-09)." + " 미충족 조건은 첫 실패에서 멈추지 않고 전부 모은다(BR-12).")
    public ApiResponse<PropertyPolicyVerdictListResponse> evaluatePolicies(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId) {
        return ApiResponse.success(policyVerdictService.evaluate(principal.memberId(), planId, propertyId));
    }

    @GetMapping("/{propertyId}/policy-verdicts")
    @Operation(summary = "매물 x 상품 판정 조회", description = "저장된 판정을 그대로 읽는다. 새로 판정하지 않는다.")
    public ApiResponse<PropertyPolicyVerdictListResponse> getPolicyVerdicts(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId) {
        return ApiResponse.success(policyVerdictService.get(principal.memberId(), planId, propertyId));
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

    @PostMapping("/compare")
    @Operation(summary = "매물 후보 비교", description = "등록 개수에는 제한이 없으며 한 번에 2~3개를 요청 순서대로 비교합니다.")
    public ApiResponse<PropertyComparisonResponse> compare(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody PropertyComparisonRequest request) {
        return ApiResponse.success(decisionService.compare(principal.memberId(), planId, request));
    }

    @PostMapping("/{propertyId}/consultations")
    @Operation(summary = "은행 상담 결과 등록", description = "같은 매물에 여러 은행·담보 방식의 상담 결과를 저장할 수 있습니다.")
    public ApiResponse<BankConsultationResponse> addConsultation(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId,
            @Valid @RequestBody BankConsultationRequest request) {
        return ApiResponse.success(decisionService.addConsultation(principal.memberId(), planId, propertyId, request));
    }

    @GetMapping("/{propertyId}/consultations")
    @Operation(summary = "매물별 은행 상담 결과 조회")
    public ApiResponse<List<BankConsultationResponse>> consultations(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId) {
        return ApiResponse.success(decisionService.consultations(principal.memberId(), planId, propertyId));
    }

    @PutMapping("/decision")
    @Operation(summary = "최종 매물·대출 조건 확정", description = "매물과 그 매물에 속한 은행 상담 결과를 함께 선택합니다.")
    public ApiResponse<PropertyDecisionResponse> decide(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody PropertyDecisionRequest request) {
        return ApiResponse.success(decisionService.decide(principal.memberId(), planId, request));
    }

    @GetMapping("/decision")
    @Operation(summary = "최종 매물·대출 조건 조회")
    public ApiResponse<PropertyDecisionResponse> decision(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(decisionService.getDecision(principal.memberId(), planId));
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
