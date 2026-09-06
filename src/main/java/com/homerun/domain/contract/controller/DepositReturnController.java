package com.homerun.domain.contract.controller;

import com.homerun.domain.contract.dto.response.LienRepaymentResponse;
import com.homerun.domain.contract.dto.response.UnreturnedDepositResponse;
import com.homerun.domain.contract.service.LienRepaymentService;
import com.homerun.domain.contract.service.UnreturnedDepositService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans/{planId}/deposit-return")
@Tag(name = "보증금 반환", description = "계약 종료 시 보증금 미반환 대응 안내(FR-HX)")
public class DepositReturnController {

    private final UnreturnedDepositService service;
    private final LienRepaymentService lienRepaymentService;

    public DepositReturnController(UnreturnedDepositService service, LienRepaymentService lienRepaymentService) {
        this.service = service;
        this.lienRepaymentService = lienRepaymentService;
    }

    @GetMapping("/unreturned-guide")
    @Operation(
            summary = "보증금 미반환 대응 안내",
            description = "보증금을 못 받았을 때 대응 순서를 안내한다(BR-33). 전입신고 유지를 최우선 경고하고,"
                    + " 반환보증 가입 여부로 이행청구(가입 O)와 법적 절차(가입 X)를 분기한다. 단계 건너뛰기는 금지한다.")
    public ApiResponse<UnreturnedDepositResponse> unreturnedGuide(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @RequestParam boolean hasReturnGuarantee) {
        return ApiResponse.success(service.guide(principal.memberId(), planId, hasReturnGuarantee));
    }

    @GetMapping("/lien-repayment")
    @Operation(
            summary = "질권 상환 자금 흐름",
            description = "퇴거 시 보증금 반환 자금 흐름을 보여준다(FR-H10-02). 보증금은 임대인이 은행에 직접"
                    + " 송금하고, 은행 몫은 대출 잔액, 내 몫은 나머지다. 착오 전액 수령 시 즉시 반환을 안내한다.")
    public ApiResponse<LienRepaymentResponse> lienRepayment(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @RequestParam long deposit,
            @RequestParam long loanBalance) {
        return ApiResponse.success(lienRepaymentService.guide(principal.memberId(), planId, deposit, loanBalance));
    }
}
