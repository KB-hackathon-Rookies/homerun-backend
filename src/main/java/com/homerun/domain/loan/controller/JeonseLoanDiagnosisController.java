package com.homerun.domain.loan.controller;

import com.homerun.domain.loan.dto.response.JeonseLoanDiagnosisResponse;
import com.homerun.domain.loan.service.JeonseLoanDiagnosisService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans/{planId}/loan-diagnosis")
@Tag(name = "전세대출 진단", description = "확정한 계획 입력으로 예상 대출 스펙과 상품별 가능성을 계산")
public class JeonseLoanDiagnosisController {

    private final JeonseLoanDiagnosisService service;

    public JeonseLoanDiagnosisController(JeonseLoanDiagnosisService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "전세대출 예상 진단", description = "청년·일반 버팀목과 은행 전세대출의 예상 가능성을 반환합니다. 실제 승인은 은행 사전심사가 필요합니다.")
    public ApiResponse<JeonseLoanDiagnosisResponse> diagnose(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.diagnose(principal.memberId(), planId));
    }
}
