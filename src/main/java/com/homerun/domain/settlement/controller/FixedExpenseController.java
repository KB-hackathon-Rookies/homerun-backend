package com.homerun.domain.settlement.controller;

import com.homerun.domain.settlement.dto.request.FixedExpenseRequest;
import com.homerun.domain.settlement.dto.response.FixedExpenseListResponse;
import com.homerun.domain.settlement.dto.response.FixedExpenseResponse;
import com.homerun.domain.settlement.service.FixedExpenseService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans/{planId}/fixed-expenses")
@Tag(name = "고정지출", description = "입주 후 고정지출 등록·조회·삭제(FR-H5-01·02)")
public class FixedExpenseController {

    private final FixedExpenseService service;

    public FixedExpenseController(FixedExpenseService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "고정지출 등록", description = "월 이자·관리비·기타 고정지출을 등록한다. 자동이체 여부도 저장한다.")
    public ApiResponse<FixedExpenseResponse> add(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody FixedExpenseRequest request) {
        return ApiResponse.success(service.add(principal.memberId(), planId, request));
    }

    @GetMapping
    @Operation(summary = "고정지출 목록", description = "등록된 고정지출과 월 합계, 연체 알림 활성 여부(이자 항목 존재)를 반환한다.")
    public ApiResponse<FixedExpenseListResponse> list(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.list(principal.memberId(), planId));
    }

    @DeleteMapping("/{expenseId}")
    @Operation(summary = "고정지출 삭제")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long expenseId) {
        service.delete(principal.memberId(), planId, expenseId);
        return ApiResponse.success(null);
    }
}
