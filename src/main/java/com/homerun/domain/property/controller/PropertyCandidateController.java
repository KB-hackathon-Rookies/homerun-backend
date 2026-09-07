package com.homerun.domain.property.controller;

import com.homerun.domain.property.dto.request.BankConsultationRequest;
import com.homerun.domain.property.dto.request.LandlordConsentRequest;
import com.homerun.domain.property.dto.request.PropertyBuildingStepRequest;
import com.homerun.domain.property.dto.request.PropertyCandidateAnalysisRequest;
import com.homerun.domain.property.dto.request.PropertyComparisonRequest;
import com.homerun.domain.property.dto.request.PropertyDecisionRequest;
import com.homerun.domain.property.dto.request.PropertyRegistryStepRequest;
import com.homerun.domain.property.dto.request.PropertyViolationStepRequest;
import com.homerun.domain.property.dto.response.BankConsultationResponse;
import com.homerun.domain.property.dto.response.LandlordConsentGuideResponse;
import com.homerun.domain.property.dto.response.PropertyCandidateAnalysisResponse;
import com.homerun.domain.property.dto.response.PropertyCandidateResponse;
import com.homerun.domain.property.dto.response.PropertyCardResponse;
import com.homerun.domain.property.dto.response.PropertyComparisonResponse;
import com.homerun.domain.property.dto.response.PropertyDecisionResponse;
import com.homerun.domain.property.dto.response.PropertyPolicyVerdictListResponse;
import com.homerun.domain.property.dto.response.PropertyRejectionResponse;
import com.homerun.domain.property.dto.response.PropertyStepSaveResponse;
import com.homerun.domain.property.dto.response.PropertyWorkflowResponse;
import com.homerun.domain.property.service.PropertyCandidateDeletionService;
import com.homerun.domain.property.service.PropertyCandidateService;
import com.homerun.domain.property.service.PropertyDecisionService;
import com.homerun.domain.property.service.PropertyPolicyVerdictService;
import com.homerun.domain.property.service.PropertyRejectionService;
import com.homerun.domain.property.service.PropertyWorkflowService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final PropertyCandidateDeletionService deletionService;
    private final PropertyDecisionService decisionService;
    private final PropertyPolicyVerdictService policyVerdictService;
    private final PropertyWorkflowService workflowService;
    private final PropertyRejectionService rejectionService;

    public PropertyCandidateController(
            PropertyCandidateService service,
            PropertyCandidateDeletionService deletionService,
            PropertyDecisionService decisionService,
            PropertyPolicyVerdictService policyVerdictService,
            PropertyWorkflowService workflowService,
            PropertyRejectionService rejectionService) {
        this.service = service;
        this.deletionService = deletionService;
        this.decisionService = decisionService;
        this.policyVerdictService = policyVerdictService;
        this.workflowService = workflowService;
        this.rejectionService = rejectionService;
    }

    @PostMapping("/{propertyId}/policy-verdicts")
    @Operation(
            summary = "매물 x 상품 판정",
            description = "이 매물로 전세 상품마다 되는지 판정하고 매물별로 남긴다(DR-10 · BR-09)." + " 미충족 조건은 첫 실패에서 멈추지 않고 전부 모은다(BR-12).")
    public ApiResponse<PropertyPolicyVerdictListResponse> evaluatePolicies(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId) {
        workflowService.requireLoanProductsReady(principal.memberId(), planId, propertyId);
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

    @GetMapping("/{propertyId}/resume")
    @Operation(summary = "매물 진단 이어하기", description = "마지막으로 저장한 STEP과 revision을 반환합니다.")
    public ApiResponse<PropertyWorkflowResponse> resume(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId) {
        return ApiResponse.success(workflowService.resume(principal.memberId(), planId, propertyId));
    }

    @PutMapping("/{propertyId}/steps/building")
    @Operation(summary = "STEP 2 주택유형·전용면적 저장", description = "자동조회가 실패하거나 사용자가 정정할 때 사용합니다.")
    public ApiResponse<PropertyStepSaveResponse> saveBuilding(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId,
            @Valid @RequestBody PropertyBuildingStepRequest request) {
        return ApiResponse.success(workflowService.saveBuilding(principal.memberId(), planId, propertyId, request));
    }

    @PutMapping("/{propertyId}/steps/violation")
    @Operation(summary = "STEP 3 위반건축물 확인 저장", description = "정부24 건축물대장을 직접 확인한 결과를 저장합니다.")
    public ApiResponse<PropertyStepSaveResponse> saveViolation(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId,
            @Valid @RequestBody PropertyViolationStepRequest request) {
        return ApiResponse.success(workflowService.saveViolation(principal.memberId(), planId, propertyId, request));
    }

    @PutMapping("/{propertyId}/steps/registry")
    @Operation(summary = "STEP 4 등기부·공시가격 확인 저장", description = "모름 값도 저장하며 RED·YELLOW·GREEN을 다시 판정합니다.")
    public ApiResponse<PropertyStepSaveResponse> saveRegistry(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId,
            @Valid @RequestBody PropertyRegistryStepRequest request) {
        return ApiResponse.success(workflowService.saveRegistry(principal.memberId(), planId, propertyId, request));
    }

    @GetMapping("/{propertyId}/card")
    @Operation(summary = "매물 통합 카드 조회", description = "진행 STEP·신호등·검증·상품·상담을 매물 하나 기준으로 반환합니다.")
    public ApiResponse<PropertyCardResponse> card(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId) {
        return ApiResponse.success(workflowService.card(principal.memberId(), planId, propertyId));
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

    @DeleteMapping("/{propertyId}")
    @Operation(summary = "매물 후보 삭제", description = "비교 후보를 삭제합니다. 최종 선택 또는 계약에 사용한 매물은 삭제할 수 없습니다.")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId) {
        deletionService.delete(principal.memberId(), planId, propertyId);
        return ApiResponse.success(null);
    }

    @PostMapping("/compare")
    @Operation(summary = "매물 후보 비교", description = "매물은 제한 없이 등록할 수 있고 한 번에 2~3개를 매물·상담 요약과 함께 비교합니다.")
    public ApiResponse<PropertyComparisonResponse> compare(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody PropertyComparisonRequest request) {
        return ApiResponse.success(decisionService.compare(principal.memberId(), planId, request));
    }

    @PostMapping("/{propertyId}/consultations")
    @Operation(summary = "은행 상담 결과 등록", description = "GREEN 이상 매물에 복수 은행 결과를 저장합니다. 모든 답변은 UNKNOWN/NOT_HEARD를 지원합니다.")
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
    @Operation(summary = "최종 매물·대출 조건 확정", description = "매물과 그 매물에 속한 대출 가능(POSSIBLE) 상담 결과를 함께 선택합니다.")
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

    @GetMapping("/{propertyId}/rejection-guidance")
    @Operation(
            summary = "거절 대응 안내",
            description = "이 매물의 은행 상담 거절 사유를 BR-24 5분류로 묶어 대안을 제시하고(FR-P8-01),"
                    + " 같은 사유가 반복되면 매물 변경을 권한다(FR-P8-02). 사람·집 자체 문제면 다른 은행이 소용없음을 표시한다.")
    public ApiResponse<PropertyRejectionResponse> rejectionGuidance(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId) {
        return ApiResponse.success(rejectionService.forProperty(principal.memberId(), planId, propertyId));
    }

    @PutMapping("/{propertyId}/landlord-consent")
    @Operation(
            summary = "임대인 협조 여부 저장",
            description = "임대인의 전세대출 협조 여부를 저장하고 상태별 안내를 반환한다(FR-P1-07·08)."
                    + " 거부(REFUSED)는 신호등을 RED 로 만들지 않는다 — 설득 스크립트를 함께 준다.")
    public ApiResponse<LandlordConsentGuideResponse> saveLandlordConsent(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId,
            @Valid @RequestBody LandlordConsentRequest request) {
        return ApiResponse.success(
                service.updateLandlordConsent(principal.memberId(), planId, propertyId, request.consent()));
    }

    @GetMapping("/{propertyId}/landlord-consent")
    @Operation(summary = "임대인 협조 상태별 안내 조회", description = "저장된 협조 상태에 맞는 경고·스크립트를 반환한다(FR-P1-08).")
    public ApiResponse<LandlordConsentGuideResponse> landlordConsentGuide(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId) {
        return ApiResponse.success(service.landlordConsentGuide(principal.memberId(), planId, propertyId));
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
