package com.homerun.domain.settlement.controller;

import com.homerun.domain.settlement.dto.request.GuaranteeFeeSupportAmountRequest;
import com.homerun.domain.settlement.dto.request.MonthlyMetricsRequest;
import com.homerun.domain.settlement.dto.request.TaxDeductionRequest;
import com.homerun.domain.settlement.dto.response.CashFlowSummaryResponse;
import com.homerun.domain.settlement.dto.response.DelinquencyRiskResponse;
import com.homerun.domain.settlement.dto.response.GuaranteeFeeSupportAmountResponse;
import com.homerun.domain.settlement.dto.response.MonthlyMetricsResponse;
import com.homerun.domain.settlement.dto.response.PostAssetReviewResponse;
import com.homerun.domain.settlement.dto.response.RateCutRightResponse;
import com.homerun.domain.settlement.dto.response.ReturnGuaranteeGuideResponse;
import com.homerun.domain.settlement.dto.response.TaxDeductionResponse;
import com.homerun.domain.settlement.service.CashFlowSummaryService;
import com.homerun.domain.settlement.service.DelinquencyRiskService;
import com.homerun.domain.settlement.service.GuaranteeFeeSupportService;
import com.homerun.domain.settlement.service.MonthlyMetricsService;
import com.homerun.domain.settlement.service.PostAssetReviewService;
import com.homerun.domain.settlement.service.RateCutRightService;
import com.homerun.domain.settlement.service.ReturnGuaranteeService;
import com.homerun.domain.settlement.service.TaxDeductionService;
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
@RequestMapping("/api/v1/plans/{planId}/settlement")
@Tag(name = "홈 정착", description = "입주 후 연말정산·현금흐름 등 정착 계산")
public class SettlementController {

    private final TaxDeductionService taxDeductionService;
    private final GuaranteeFeeSupportService guaranteeFeeSupportService;
    private final MonthlyMetricsService monthlyMetricsService;
    private final CashFlowSummaryService cashFlowSummaryService;
    private final RateCutRightService rateCutRightService;
    private final ReturnGuaranteeService returnGuaranteeService;
    private final PostAssetReviewService postAssetReviewService;
    private final DelinquencyRiskService delinquencyRiskService;

    public SettlementController(
            TaxDeductionService taxDeductionService,
            GuaranteeFeeSupportService guaranteeFeeSupportService,
            MonthlyMetricsService monthlyMetricsService,
            CashFlowSummaryService cashFlowSummaryService,
            RateCutRightService rateCutRightService,
            ReturnGuaranteeService returnGuaranteeService,
            PostAssetReviewService postAssetReviewService,
            DelinquencyRiskService delinquencyRiskService) {
        this.taxDeductionService = taxDeductionService;
        this.guaranteeFeeSupportService = guaranteeFeeSupportService;
        this.monthlyMetricsService = monthlyMetricsService;
        this.cashFlowSummaryService = cashFlowSummaryService;
        this.rateCutRightService = rateCutRightService;
        this.returnGuaranteeService = returnGuaranteeService;
        this.postAssetReviewService = postAssetReviewService;
        this.delinquencyRiskService = delinquencyRiskService;
    }

    @PostMapping("/tax-deduction")
    @Operation(
            summary = "주택임차차입금 소득공제 예상",
            description = "전세대출 원리금상환액 소득공제(BR-29)의 공제·환급액을 계산한다. 만기일시상환은 이자만 대상이다."
                    + " 간이세율은 화면 예시용 가정이라 실제 환급과 다를 수 있음을 함께 표시한다.")
    public ApiResponse<TaxDeductionResponse> taxDeduction(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody TaxDeductionRequest request) {
        return ApiResponse.success(taxDeductionService.forPlan(principal.memberId(), planId, request));
    }

    @PostMapping("/guarantee-fee-support")
    @Operation(
            summary = "보증료 지원 예상 금액",
            description = "전세보증금 반환보증 보증료 지원액을 계산한다(BR-31). 청년·신혼은 전액, 청년 외는 90%,"
                    + " 한도는 가입일 기준(2025-03-31 이후 40만 / 이전 30만). 자격 판정은 2루(GTE-01-04)에서 한다.")
    public ApiResponse<GuaranteeFeeSupportAmountResponse> guaranteeFeeSupport(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody GuaranteeFeeSupportAmountRequest request) {
        return ApiResponse.success(guaranteeFeeSupportService.forPlan(principal.memberId(), planId, request));
    }

    @PostMapping("/monthly-metrics")
    @Operation(
            summary = "월간 지표(월 잔여금·RIR)",
            description = "월 이자·주거비·잔여금·RIR을 계산한다(BR-28). 계획값이든 실제값이든 같은 공식이다."
                    + " RIR 안정/위험 컷오프는 공식 출처 미확보(O-3)라 숫자만 주고 판정하지 않는다.")
    public ApiResponse<MonthlyMetricsResponse> monthlyMetrics(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody MonthlyMetricsRequest request) {
        return ApiResponse.success(monthlyMetricsService.forPlan(principal.memberId(), planId, request));
    }

    @GetMapping("/cash-flow")
    @Operation(
            summary = "현금흐름 종합",
            description =
                    "저장된 대출·고정지출·소득·생활비로 월간 지표(BR-28)를 계산한다. 대출이 등록돼 있어야 한다." + " RIR 컷오프는 공식 출처 미확보(O-3)라 판정하지 않는다.")
    public ApiResponse<CashFlowSummaryResponse> cashFlow(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(cashFlowSummaryService.forPlan(principal.memberId(), planId));
    }

    @GetMapping("/rate-cut-right")
    @Operation(
            summary = "금리인하요구권 안내",
            description = "실행 대출 상품에 따라 금리인하요구권 안내를 분기한다(FR-H6-01). 버팀목(기금)대출은 비대상이라"
                    + " 신청 안내 대신 우대금리 추가 안내를 준다. 은행 자체 대출만 신청 사유·경로를 노출한다.")
    public ApiResponse<RateCutRightResponse> rateCutRight(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(rateCutRightService.forPlan(principal.memberId(), planId));
    }

    @GetMapping("/return-guarantee")
    @Operation(
            summary = "반환보증 가입 안내",
            description = "실행 대출 담보에 따라 반환보증 가입 안내를 분기한다(FR-H1-01). HUG 안심전세는 이미 포함돼"
                    + " 있어 숨기고, HF·SGI·채권양도 등은 가입 시기·경로·서류를 노출한다.")
    public ApiResponse<ReturnGuaranteeGuideResponse> returnGuarantee(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(returnGuaranteeService.forPlan(principal.memberId(), planId));
    }

    @GetMapping("/post-asset-review")
    @Operation(
            summary = "사후자산심사 안내",
            description = "기금(버팀목)대출만 사후자산심사를 노출한다(FR-H3-01). 빠뜨리기 쉬운 자산 항목과 주의"
                    + "(입주 후 자산 증가 무관·가산금리 비가역)를 안내한다. 은행 자체 대출은 생략한다.")
    public ApiResponse<PostAssetReviewResponse> postAssetReview(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(postAssetReviewService.forPlan(principal.memberId(), planId));
    }

    @GetMapping("/delinquency-risk")
    @Operation(
            summary = "연체 위험 판단",
            description = "월 잔여금이 적자면 연체 위험으로 경고한다(FR-H5-04). 이자 고정지출이 없으면 판단할 수 없어"
                    + " 등록을 안내하고, 이자 자동이체 미등록이면 연체이자(BR-23) 경고를 함께 준다.")
    public ApiResponse<DelinquencyRiskResponse> delinquencyRisk(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(delinquencyRiskService.forPlan(principal.memberId(), planId));
    }
}
