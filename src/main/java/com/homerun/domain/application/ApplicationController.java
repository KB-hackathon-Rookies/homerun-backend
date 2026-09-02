package com.homerun.domain.application;

import com.homerun.domain.application.ApplicationDtos.ApplicationList;
import com.homerun.domain.application.ApplicationDtos.ApplicationView;
import com.homerun.domain.application.ApplicationDtos.CreateRequest;
import com.homerun.domain.application.ApplicationDtos.UpdateRequest;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 신청 실행(APP-01). 실제 기관 제출은 하지 않는다(OUT-01-03). */
@RestController
@RequestMapping("/api/v1/plans/{planId}/applications")
@Tag(name = "신청 실행", description = "신청 건 생성과 진행 상태·결과 기록")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "신청 목록 조회")
    public ApiResponse<ApplicationList> list(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.list(principal.memberId(), planId));
    }

    @PostMapping
    @Operation(summary = "신청 건 생성", description = "같은 계획에서 같은 정책을 두 번 신청할 수 없다.")
    public ResponseEntity<ApiResponse<ApplicationView>> create(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.create(principal.memberId(), planId, request)));
    }

    @PatchMapping("/{applicationId}")
    @Operation(
            summary = "진행 상태·결과 기록",
            description = "거절이면 은행·보증기관·서류·상품 중 어디서 막혔는지를 함께 받는다. 단계에 맞는 다음 행동을 nextAction 으로 돌려준다.")
    public ApiResponse<ApplicationView> update(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long applicationId,
            @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.success(service.update(principal.memberId(), planId, applicationId, request));
    }
}
