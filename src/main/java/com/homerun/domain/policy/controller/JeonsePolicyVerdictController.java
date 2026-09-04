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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans/{planId}/policies/jeonse")
@Tag(name = "전세대출 정책 판정", description = "policy_rule 조건식으로 전세대출·반환보증 자격을 판정하고 근거를 남긴다")
public class JeonsePolicyVerdictController {

    private final JeonsePolicyVerdictService service;

    public JeonsePolicyVerdictController(JeonsePolicyVerdictService service) {
        this.service = service;
    }

    @PostMapping("/evaluate")
    @Operation(
            summary = "전세대출 정책 판정",
            description = "청년/일반 버팀목, 서울시 이자지원을 판정한다. propertyId 없이는 사람 조건만 본 예상 판정이고,"
                    + " propertyId 를 주면 집 조건(위반건축물·다가구)까지 같이 봐서 최종 승인 여부에 가까워진다.")
    public ApiResponse<JeonsePolicyVerdictListResponse> evaluate(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @RequestParam(required = false) Long propertyId) {
        return ApiResponse.success(service.evaluate(principal.memberId(), planId, propertyId));
    }

    @PostMapping("/properties/{propertyId}/return-guarantees/evaluate")
    @Operation(summary = "반환보증 판정", description = "매물 기준(공시가격 등)으로 HUG/HF/SGI 반환보증 가입 가능성을 판정한다.")
    public ApiResponse<JeonsePolicyVerdictListResponse> evaluateReturnGuarantees(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long propertyId) {
        return ApiResponse.success(service.evaluateReturnGuarantees(principal.memberId(), planId, propertyId));
    }
}
