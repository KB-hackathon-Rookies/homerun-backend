package com.homerun.domain.policy.controller;

import com.homerun.domain.policy.dto.response.JeonsePolicyVerdictListResponse;
import com.homerun.domain.policy.dto.response.PreferentialRateChangeResponse;
import com.homerun.domain.policy.service.JeonsePolicyVerdictService;
import com.homerun.domain.policy.service.PreferentialRateChangeService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans/{planId}/policies/jeonse")
@Tag(name = "전세대출 정책 판정", description = "policy_rule 조건식으로 전세대출·반환보증 자격을 판정하고 근거를 남긴다")
public class JeonsePolicyVerdictController {

    private final JeonsePolicyVerdictService service;
    private final PreferentialRateChangeService preferentialRateChangeService;

    public JeonsePolicyVerdictController(
            JeonsePolicyVerdictService service, PreferentialRateChangeService preferentialRateChangeService) {
        this.service = service;
        this.preferentialRateChangeService = preferentialRateChangeService;
    }

    @PostMapping("/evaluate")
    @Operation(
            summary = "전세대출 정책 판정",
            description = "청년/일반 버팀목, 서울시 이자지원을 판정한다. propertyId 없이는 사람 조건만 본 예상 판정이고,"
                    + " propertyId 를 주면 집 조건(위반건축물·다가구)까지 같이 봐서 최종 승인 여부에 가까워진다.")
    public ApiResponse<JeonsePolicyVerdictListResponse> evaluate(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @RequestParam(required = false) Long propertyId) {
        return ApiResponse.success(service.evaluate(principal.memberId(), planId, propertyId));
    }

    @PostMapping("/properties/{propertyId}/return-guarantees/evaluate")
    @Operation(summary = "반환보증 판정", description = "매물 기준(공시가격 등)으로 HUG/HF/SGI 반환보증 가입 가능성을 판정한다.")
    public ApiResponse<JeonsePolicyVerdictListResponse> evaluateReturnGuarantees(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId) {
        return ApiResponse.success(service.evaluateReturnGuarantees(principal.memberId(), planId, propertyId));
    }

    @PostMapping("/guarantee-fee-support/evaluate")
    @Operation(
            summary = "보증료 지원 판정",
            description = "전세보증금 반환보증료 지원사업 자격을 소득 기준으로 판정한다(GTE-01-04). 선착순 예산 소진 사업이라" + " PASS 가 최종 지원 확정을 뜻하지 않는다.")
    public ApiResponse<JeonsePolicyVerdictListResponse> evaluateGuaranteeFeeSupport(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.evaluateGuaranteeFeeSupport(principal.memberId(), planId));
    }

    @GetMapping("/preferential-rate-changes")
    @Operation(
            summary = "우대금리 승격 감지",
            description = "직전 입력 대비 새로 우대금리 대상이 됐는지 확인한다(POL-01-06). 가구원수가 필요한 단독세대주"
                    + " 조건 등은 아직 못 본다 — 중소기업·창업기업 취업 조건만 다룬다.")
    public ApiResponse<PreferentialRateChangeResponse> preferentialRateChanges(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(preferentialRateChangeService.detect(principal.memberId(), planId));
    }
}
