package com.homerun.domain.contract.controller;

import com.homerun.domain.contract.dto.request.ContractCancellationRequest;
import com.homerun.domain.contract.dto.response.ContractCancellationResponse;
import com.homerun.domain.contract.service.ContractCancellationService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans/{planId}/contract")
@Tag(name = "계약 해제 대응", description = "대출 거절 후 계약 해제 가능성·특약 발동·잔여일 대안 안내(FR-P8)")
public class ContractCancellationController {

    private final ContractCancellationService service;

    public ContractCancellationController(ContractCancellationService service) {
        this.service = service;
    }

    @PostMapping("/cancellation-guide")
    @Operation(
            summary = "계약 해제 대응 안내",
            description = "계약 진행 단계로 해제 가능성을 판정하고(FR-P8-04), 특약이 있으면 발동 절차를(FR-P8-03),"
                    + " 없으면 잔금일까지 남은 일수로 대안을 분기한다(FR-P8-05). 잔여일 기준은 권장치이며 법정 기한이 아니다.")
    public ApiResponse<ContractCancellationResponse> cancellationGuide(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody ContractCancellationRequest request) {
        return ApiResponse.success(service.guide(principal.memberId(), planId, request));
    }
}
