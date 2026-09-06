package com.homerun.domain.property.controller;

import com.homerun.domain.property.dto.response.PropertySearchGuideResponse;
import com.homerun.domain.property.service.PropertySearchGuideService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 온라인 매물 탐색 안내(FR-P0, page 2-0). 판정이 아니라 안내라 계획을 고치지 않는다. */
@RestController
@RequestMapping("/api/v1/plans/{planId}/property-search-guide")
@Tag(name = "매물 탐색 안내", description = "2루 진입 전 매물 사이트 필터·탐색처·미리 거르기 안내(FR-P0)")
public class PropertySearchGuideController {

    private final PropertySearchGuideService service;

    public PropertySearchGuideController(PropertySearchGuideService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "온라인 매물 탐색 안내",
            description =
                    "1루 조건(보증금·지역·면적·주택유형)을 매물 사이트 필터 형태로 정리하고," + " 탐색처 링크와 미리 거르기 경고 4가지를 함께 준다. 매물 사이트 연동은 범위 밖이다.")
    public ApiResponse<PropertySearchGuideResponse> guide(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.forPlan(principal.memberId(), planId));
    }
}
