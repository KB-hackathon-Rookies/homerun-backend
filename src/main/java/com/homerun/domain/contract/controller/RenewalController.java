package com.homerun.domain.contract.controller;

import com.homerun.domain.contract.dto.response.RenewalMethodsResponse;
import com.homerun.domain.contract.service.RenewalMethodsService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans/{planId}/renewal")
@Tag(name = "계약 갱신", description = "계약 갱신 방법·재심사 안내(FR-H9)")
public class RenewalController {

    private final RenewalMethodsService service;

    public RenewalController(RenewalMethodsService service) {
        this.service = service;
    }

    @GetMapping("/methods")
    @Operation(
            summary = "갱신 방법 3종 비교",
            description = "계약갱신청구권·묵시적 갱신·합의 갱신을 비교한다(FR-H9-02). 5%·1회·통보시기는 config_effective"
                    + " 에서 읽는다. 계약갱신청구권은 1회만 쓸 수 있음을 안내한다.")
    public ApiResponse<RenewalMethodsResponse> methods(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.forPlan(principal.memberId(), planId));
    }
}
