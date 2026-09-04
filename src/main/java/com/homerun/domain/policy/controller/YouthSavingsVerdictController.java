package com.homerun.domain.policy.controller;

import com.homerun.domain.policy.dto.response.PolicyVerdictResponse;
import com.homerun.domain.policy.service.YouthSavingsVerdictService;
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
@RequestMapping("/api/v1/plans/{planId}/policies/youth-savings")
@Tag(name = "청년미래적금 판정", description = "연령·소득 조건으로 청년미래적금 자격을 판정한다(POL-02-02)")
public class YouthSavingsVerdictController {

    private final YouthSavingsVerdictService service;

    public YouthSavingsVerdictController(YouthSavingsVerdictService service) {
        this.service = service;
    }

    @PostMapping("/evaluate")
    @Operation(
            summary = "청년미래적금 판정",
            description =
                    "연령·개인소득을 판정한다. 가구소득·최근 3년 금융소득종합과세 이력은 걷지 않는 값이라" + " 항상 추가 확인으로 남는다 — 이 정책은 사실상 PASS가 나오지 않는다.")
    public ApiResponse<PolicyVerdictResponse> evaluate(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.evaluate(principal.memberId(), planId));
    }
}
