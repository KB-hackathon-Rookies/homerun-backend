package com.homerun.domain.openbanking.controller;

import com.homerun.domain.openbanking.dto.response.FinancialSnapshotResponse;
import com.homerun.domain.openbanking.service.FinancialSnapshotService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/open-banking/financial-snapshots")
@Tag(name = "오픈뱅킹", description = "금융결제원 OAuth 연결 및 본인 계좌·잔액·거래내역 조회")
public class FinancialSnapshotController {

    private final FinancialSnapshotService service;

    public FinancialSnapshotController(FinancialSnapshotService service) {
        this.service = service;
    }

    @GetMapping("/latest")
    @Operation(
            summary = "최근 금융정보 스냅샷 조회",
            description = "마지막 계획 금융정보 동기화에서 안전하게 계산해 저장한 값을 반환합니다. 외부 API를 다시 호출하지 않습니다.")
    public ApiResponse<FinancialSnapshotResponse> latest(@AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.success(service.latest(principal.memberId()));
    }
}
