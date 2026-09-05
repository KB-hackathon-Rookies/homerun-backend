package com.homerun.domain.alternative.controller;

import com.homerun.domain.alternative.dto.response.CausesResponse;
import com.homerun.domain.alternative.dto.response.RetryQueueResponse;
import com.homerun.domain.alternative.service.AlternativeCauseService;
import com.homerun.domain.alternative.service.RetryQueueService;
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
@RequestMapping("/api/v1/plans/{planId}/alternatives")
@Tag(name = "대안 경로", description = "정책 탈락 후 재도전·대안 경로 안내(ALT-01)")
public class AlternativeController {

    private final RetryQueueService retryQueueService;
    private final AlternativeCauseService alternativeCauseService;

    public AlternativeController(RetryQueueService retryQueueService, AlternativeCauseService alternativeCauseService) {
        this.retryQueueService = retryQueueService;
        this.alternativeCauseService = alternativeCauseService;
    }

    @GetMapping("/retry-queue")
    @Operation(
            summary = "재도전 큐",
            description = "나이 하한을 아직 못 채운 정책을 채우는 예정일 순으로 보여준다(ALT-01-04)." + " 소득·자산처럼 언제 바뀔지 알 수 없는 조건은 다루지 않는다.")
    public ApiResponse<RetryQueueResponse> retryQueue(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(retryQueueService.build(principal.memberId(), planId));
    }

    @GetMapping("/causes")
    @Operation(
            summary = "미충족 원인 분석",
            description = "정책 탈락의 핵심 원인을 조건별로 분리해 보여준다(ALT-01-01). 이미 저장된 판정 결과를 모아 보여줄 뿐 새로 판정하지 않는다.")
    public ApiResponse<CausesResponse> causes(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(alternativeCauseService.causes(principal.memberId(), planId));
    }
}
