package com.homerun.domain.plan.controller;

import com.homerun.domain.plan.dto.response.PlanProfilePrefillResponse;
import com.homerun.domain.plan.service.PlanProfilePrefillService;
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
@RequestMapping("/api/v1/plans/{planId}/input/profile-prefill")
@Tag(name = "계획 입력")
public class PlanProfilePrefillController {
    private final PlanProfilePrefillService service;

    public PlanProfilePrefillController(PlanProfilePrefillService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "회원정보 기반 진단 초깃값 조회",
            description =
                    "소유한 계획만 조회합니다. 명시적 모름/저장값을 우선하며 빈 필드만 회원정보로 제안합니다. 입력·이력·진행상태는 변경하지 않습니다. 프론트의 미저장 수정값도 덮어쓰지 않아야 합니다.")
    public ApiResponse<PlanProfilePrefillResponse> get(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.get(principal.memberId(), planId));
    }
}
