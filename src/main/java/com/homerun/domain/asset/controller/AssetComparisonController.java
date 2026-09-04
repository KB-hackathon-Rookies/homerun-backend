package com.homerun.domain.asset.controller;

import com.homerun.domain.asset.dto.request.AssetComparisonRequest;
import com.homerun.domain.asset.dto.response.AssetComparisonResponse;
import com.homerun.domain.asset.service.AssetComparisonService;
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
@RequestMapping("/api/v1/plans/{planId}/assets")
@Tag(name = "자산 처분 비교", description = "IRP·주택청약을 깨서 보증금을 만드는 선택을 대출과 비교한다(AST-01)")
public class AssetComparisonController {

    private final AssetComparisonService service;

    public AssetComparisonController(AssetComparisonService service) {
        this.service = service;
    }

    @PostMapping("/compare")
    @Operation(summary = "자산 처분 비교", description = "IRP는 세금까지 계산한다. 주택청약은 추징액을 계산할 근거가 없어 사실 안내만 한다.")
    public ApiResponse<AssetComparisonResponse> compare(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody AssetComparisonRequest request) {
        return ApiResponse.success(service.compare(principal.memberId(), planId, request));
    }
}
