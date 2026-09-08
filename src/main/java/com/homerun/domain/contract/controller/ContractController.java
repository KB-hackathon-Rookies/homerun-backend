package com.homerun.domain.contract.controller;

import com.homerun.domain.contract.dto.ContractDtos.BalanceDateRequest;
import com.homerun.domain.contract.dto.ContractDtos.ContractGuide;
import com.homerun.domain.contract.dto.ContractDtos.SaveRequest;
import com.homerun.domain.contract.dto.request.RegistrySnapshotRequest;
import com.homerun.domain.contract.dto.request.ThirdBaseCompleteRequest;
import com.homerun.domain.contract.dto.response.ContractEntryResponse;
import com.homerun.domain.contract.dto.response.ContractScheduleResponse;
import com.homerun.domain.contract.dto.response.RegistryComparisonResponse;
import com.homerun.domain.contract.dto.response.ThirdBaseCompleteResponse;
import com.homerun.domain.contract.service.ContractEntryService;
import com.homerun.domain.contract.service.ContractScheduleService;
import com.homerun.domain.contract.service.ContractService;
import com.homerun.domain.contract.service.RegistryComparisonService;
import com.homerun.domain.contract.service.ThirdBaseCompletionService;
import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.PropertyVerification;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final ContractEntryService entries;
    private final ThirdBaseCompletionService completions;
    private final ContractScheduleService schedules;
    private final RegistryComparisonService registries;

    public ContractController(
            ContractService service,
            ContractEntryService entries,
            ThirdBaseCompletionService completions,
            ContractScheduleService schedules,
            RegistryComparisonService registries) {
        this.service = service;
        this.entries = entries;
        this.completions = completions;
        this.schedules = schedules;
        this.registries = registries;
    }

    @PostMapping("/prefill")
    @Operation(summary = "2루 선택값으로 3루 계약 초안 생성", description = "확정 매물·상담 결과만 동기화하며 3루에서 입력한 날짜와 금액은 유지합니다.")
    public ApiResponse<ContractEntryResponse> prefill(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(entries.prefill(principal.memberId(), planId));
    }

    @PostMapping("/complete")
    @Operation(summary = "3루 완료", description = "잔금 지급·전입신고·잔금일 등기부 안전 대조를 확인하고 HOME 단계로 넘깁니다.")
    public ApiResponse<ThirdBaseCompleteResponse> complete(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody ThirdBaseCompleteRequest request) {
        return ApiResponse.success(completions.complete(principal.memberId(), planId, request));
    }

    @GetMapping("/schedule")
    @Operation(summary = "잔금일 기준 실행 일정", description = "상품·담보·신청방법·주택유형에 따라 D-day 일정을 분기합니다.")
    public ApiResponse<ContractScheduleResponse> schedule(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(schedules.get(principal.memberId(), planId));
    }

    @PostMapping("/registry-snapshots")
    @Operation(summary = "계약·잔금일 등기부 기록", description = "계약 체결 시점과 잔금일 등기부를 각각 저장하고 즉시 대조합니다.")
    public ApiResponse<RegistryComparisonResponse> recordRegistry(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody RegistrySnapshotRequest request) {
        return ApiResponse.success(registries.record(principal.memberId(), planId, request));
    }

    @GetMapping("/registry-comparison")
    @Operation(summary = "등기부 변경 위험 대조")
    public ApiResponse<RegistryComparisonResponse> compareRegistry(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(registries.compare(principal.memberId(), planId));
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

    @PatchMapping("/balance-date")
    @Operation(
            summary = "잔금 예정일만 수정",
            description = "다른 계약 값(계약일·확정일자·상담일 등)은 보존한다. 기존 계약을 다시 열어 잔금일만 바꿀 때 쓴다.")
    public ApiResponse<ContractGuide> saveBalanceDate(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody BalanceDateRequest request) {
        return ApiResponse.success(service.saveBalanceDate(principal.memberId(), planId, request.balanceDate()));
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
