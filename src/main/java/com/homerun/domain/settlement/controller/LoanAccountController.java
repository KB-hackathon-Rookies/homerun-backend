package com.homerun.domain.settlement.controller;

import com.homerun.domain.settlement.dto.request.LoanAccountRequest;
import com.homerun.domain.settlement.dto.response.LoanAccountResponse;
import com.homerun.domain.settlement.service.LoanAccountService;
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

@RestController
@RequestMapping("/api/v1/plans/{planId}/loan-account")
@Tag(name = "실행 대출", description = "입주 후 실행된 전세대출 등록·조회(DR-20)")
public class LoanAccountController {

    private final LoanAccountService service;

    public LoanAccountController(LoanAccountService service) {
        this.service = service;
    }

    @PutMapping
    @Operation(summary = "실행 대출 등록", description = "실행된 대출을 계획당 1건 저장한다. 다시 등록하면 덮어쓴다.")
    public ApiResponse<LoanAccountResponse> register(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody LoanAccountRequest request) {
        return ApiResponse.success(service.register(principal.memberId(), planId, request));
    }

    @GetMapping
    @Operation(summary = "실행 대출 조회", description = "저장된 대출과 월 이자(원금×금리÷12)를 반환한다.")
    public ApiResponse<LoanAccountResponse> get(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.get(principal.memberId(), planId));
    }
}
