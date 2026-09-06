package com.homerun.domain.settlement.controller;

import com.homerun.domain.settlement.dto.request.GuaranteeFeeSupportAmountRequest;
import com.homerun.domain.settlement.dto.request.TaxDeductionRequest;
import com.homerun.domain.settlement.dto.response.GuaranteeFeeSupportAmountResponse;
import com.homerun.domain.settlement.dto.response.TaxDeductionResponse;
import com.homerun.domain.settlement.service.GuaranteeFeeSupportService;
import com.homerun.domain.settlement.service.TaxDeductionService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    public SettlementController(
            TaxDeductionService taxDeductionService, GuaranteeFeeSupportService guaranteeFeeSupportService) {
        this.taxDeductionService = taxDeductionService;
        this.guaranteeFeeSupportService = guaranteeFeeSupportService;
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
}
