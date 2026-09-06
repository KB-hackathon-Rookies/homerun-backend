package com.homerun.domain.contract.controller;

import com.homerun.domain.contract.dto.response.LoanTransferGuideResponse;
import com.homerun.domain.contract.dto.response.MoveOutChecklistResponse;
import com.homerun.domain.contract.dto.response.MoveOutScheduleResponse;
import com.homerun.domain.contract.service.LoanTransferGuideService;
import com.homerun.domain.contract.service.MoveOutChecklistService;
import com.homerun.domain.contract.service.MoveOutScheduleService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans/{planId}/move-out")
@Tag(name = "퇴거", description = "계약 종료 퇴거 시 체크리스트(FR-H10)")
public class MoveOutController {

    private final MoveOutChecklistService service;
    private final MoveOutScheduleService scheduleService;
    private final LoanTransferGuideService loanTransferGuideService;

    public MoveOutController(
            MoveOutChecklistService service,
            MoveOutScheduleService scheduleService,
            LoanTransferGuideService loanTransferGuideService) {
        this.service = service;
        this.scheduleService = scheduleService;
        this.loanTransferGuideService = loanTransferGuideService;
    }

    @GetMapping("/checklist")
    @Operation(
            summary = "퇴거 체크리스트",
            description = "퇴거 시 챙길 항목을 준다(FR-H10-04). 아파트·오피스텔은 장기수선충당금 정산, 반환보증"
                    + " 가입자는 해지 항목이 해당된다. 해당 없는 항목은 applicable=false 로 표시한다.")
    public ApiResponse<MoveOutChecklistResponse> checklist(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @RequestParam boolean aptOrOfficetel,
            @RequestParam boolean hasReturnGuarantee) {
        return ApiResponse.success(service.guide(principal.memberId(), planId, aptOrOfficetel, hasReturnGuarantee));
    }

    @GetMapping("/schedule")
    @Operation(
            summary = "퇴거 일정 안내",
            description =
                    "퇴거 일정을 단계별로 안내한다(FR-H10-01). 법정 기한(종료 통보·전입신고 14일)과 권장(사전" + " 통지)을 구분한다. 전입신고 기한은 이사일에서 계산한다.")
    public ApiResponse<MoveOutScheduleResponse> schedule(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate moveOutDate) {
        return ApiResponse.success(scheduleService.forPlan(principal.memberId(), planId, moveOutDate));
    }

    @GetMapping("/loan-transfer")
    @Operation(
            summary = "대출 승계·이전 안내",
            description = "새 집으로 대출을 이어가는 방법(상환 후 신규 / 임차목적물 변경)을 안내한다(FR-H10-03)." + " 은행마다 달라 이사 계획 시 즉시 문의하도록 강조한다.")
    public ApiResponse<LoanTransferGuideResponse> loanTransfer(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(loanTransferGuideService.forPlan(principal.memberId(), planId));
    }
}
