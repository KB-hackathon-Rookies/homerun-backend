package com.homerun.domain.policy.controller;

import com.homerun.domain.policy.dto.response.JeonsePolicyVerdictListResponse;
import com.homerun.domain.policy.service.JeonsePolicyVerdictService;
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
@RequestMapping("/api/v1/plans/{planId}/policies/jeonse")
@Tag(name = "전세대출 정책 판정", description = "policy_rule 조건식으로 청년/일반 버팀목·서울시 이자지원 자격을 판정하고 근거를 남긴다")
public class JeonsePolicyVerdictController {

    private final JeonsePolicyVerdictService service;

    public JeonsePolicyVerdictController(JeonsePolicyVerdictService service) {
        this.service = service;
    }

    @PostMapping("/evaluate")
    @Operation(summary = "전세대출 정책 판정", description = "plan_input 기반 조건만 판정한다. 반환보증(HUG/HF/SGI)은 아직 대상이 아니다.")
    public ApiResponse<JeonsePolicyVerdictListResponse> evaluate(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.evaluate(principal.memberId(), planId));
    }
}
