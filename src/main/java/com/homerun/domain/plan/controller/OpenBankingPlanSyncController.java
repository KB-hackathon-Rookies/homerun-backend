package com.homerun.domain.plan.controller;

import com.homerun.domain.plan.dto.response.PlanFinancialSyncResponse;
import com.homerun.domain.plan.service.OpenBankingPlanSyncService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans/{planId}/input/open-banking-sync")
@Tag(name = "계획 입력", description = "진단 입력 자동 저장과 외부 금융정보 동기화")
public class OpenBankingPlanSyncController {
    private final OpenBankingPlanSyncService service;

    public OpenBankingPlanSyncController(OpenBankingPlanSyncService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "오픈뱅킹 금융정보를 계획 입력에 동기화",
            description =
                    "최근 완료 3개월의 모든 등록 계좌 거래 조회에 성공하고 매월 급여 거래가 확인될 때만 월 실수령 추정값을 미확인 상태로 저장합니다. 사용자 확인 전 정책 자격 판정에는 사용하지 않습니다. 계좌 출금가능액·대출 상환액은 참고용으로 반환하며 순자산·가용현금으로 자동 저장하지 않습니다.")
    public ApiResponse<PlanFinancialSyncResponse> sync(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Parameter(description = "추가 조회할 금융기관 코드(숫자 3자리)") @RequestParam(required = false) List<String> bankCodes) {
        return ApiResponse.success(service.sync(principal.memberId(), planId, bankCodes));
    }
}
