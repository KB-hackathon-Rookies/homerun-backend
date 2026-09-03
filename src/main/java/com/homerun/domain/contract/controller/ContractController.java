package com.homerun.domain.contract.controller;

import com.homerun.domain.contract.dto.ContractDtos.ContractGuide;
import com.homerun.domain.contract.dto.ContractDtos.SaveRequest;
import com.homerun.domain.contract.service.ContractService;
import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.PropertyVerification;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 계약 실행(PRP-02)과 매물 검증(PRP-01). */
@RestController
@RequestMapping("/api/v1/plans/{planId}/contract")
@Tag(name = "계약 실행", description = "계약 전 확인, 권장 특약, 진행상태, 잔금·전입 안내")
public class ContractController {

    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "계약 실행 안내 조회", description = "계약 정보를 아직 저장하지 않았어도 계약 전 확인 사항은 내려간다.")
    public ApiResponse<ContractGuide> guide(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.guide(principal.memberId(), planId));
    }

    @PutMapping
    @Operation(summary = "계약 정보 저장", description = "계획당 한 건이다. 폼 전체를 덮어쓴다.")
    public ApiResponse<ContractGuide> save(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody SaveRequest request) {
        return ApiResponse.success(service.save(principal.memberId(), planId, request));
    }

    @PostMapping("/risk-check")
    @Operation(summary = "매물 안전검증", description = "등기부·건축물대장·공시가격에서 읽은 사실로 판정한다. 보증금이 있는 월세도 전세와 같은 기준을 적용한다.")
    public ApiResponse<PropertyVerification> riskCheck(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody PropertyFacts facts) {
        return ApiResponse.success(service.riskCheck(principal.memberId(), planId, facts));
    }
}
