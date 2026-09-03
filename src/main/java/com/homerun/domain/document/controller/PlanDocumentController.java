package com.homerun.domain.document.controller;

import com.homerun.domain.document.dto.HoldingDtos.HoldingList;
import com.homerun.domain.document.dto.HoldingDtos.RecordRequest;
import com.homerun.domain.document.service.DocumentHoldingService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 계획별 서류 보유 현황(EVI-01-04 · EVI-01-07). 카탈로그와 달리 사용자마다 다르다. */
@RestController
@RequestMapping("/api/v1/plans/{planId}/documents")
@Tag(name = "서류 준비 현황", description = "보유 상태와 인정 기간")
public class PlanDocumentController {

    private final DocumentHoldingService service;

    public PlanDocumentController(DocumentHoldingService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "서류 준비 현황 조회", description = "인정 기간이 지난 서류는 재발급 필요로 나온다.")
    public ApiResponse<HoldingList> list(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.list(principal.memberId(), planId));
    }

    @PutMapping
    @Operation(summary = "서류 상태 기록", description = "재발급 필요는 발급일로 판정하므로 직접 지정할 수 없다.")
    public ApiResponse<HoldingList> record(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody RecordRequest request) {
        return ApiResponse.success(service.record(principal.memberId(), planId, request));
    }
}
