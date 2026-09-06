package com.homerun.domain.contract.controller;

import com.homerun.domain.contract.dto.response.MoveOutChecklistResponse;
import com.homerun.domain.contract.service.MoveOutChecklistService;
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
@RequestMapping("/api/v1/plans/{planId}/move-out")
@Tag(name = "퇴거", description = "계약 종료 퇴거 시 체크리스트(FR-H10)")
public class MoveOutController {

    private final MoveOutChecklistService service;

    public MoveOutController(MoveOutChecklistService service) {
        this.service = service;
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
}
