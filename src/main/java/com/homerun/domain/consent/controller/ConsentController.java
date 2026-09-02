package com.homerun.domain.consent.controller;

import com.homerun.domain.consent.dto.ConsentDtos.ConsentOverview;
import com.homerun.domain.consent.dto.ConsentDtos.IssueRequest;
import com.homerun.domain.consent.dto.ConsentDtos.IssueResponse;
import com.homerun.domain.consent.service.ConsentService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 가구원 확인·동의(FAM-01). */
@RestController
@RequestMapping("/api/v1/plans/{planId}/consents")
@Tag(name = "가구원 동의", description = "일회용 동의 링크 발급과 응답 추적")
public class ConsentController {

    private final ConsentService service;

    public ConsentController(ConsentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "동의 현황 조회", description = "가구원별 확인 상태를 준다. allVerified 가 false 면 자격을 확정하지 말고 추가확인으로 남겨야 한다.")
    public ApiResponse<ConsentOverview> overview(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(service.overview(principal.memberId(), planId));
    }

    @PostMapping
    @Operation(summary = "일회용 동의 링크 발급", description = "원문 토큰은 이 응답에서만 볼 수 있다. 서버는 해시만 저장한다.")
    public ResponseEntity<ApiResponse<IssueResponse>> issue(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody IssueRequest request) {
        // 링크가 담긴 응답이 중계 서버나 브라우저 히스토리에 남지 않게 한다.
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .body(ApiResponse.success(service.issue(principal.memberId(), planId, request)));
    }

    @PostMapping("/{consentId}/reissue")
    @Operation(summary = "동의 링크 재발급", description = "이전 링크는 폐기된다. 유효한 링크가 둘이 되지 않게 한다.")
    public ResponseEntity<ApiResponse<IssueResponse>> reissue(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable Long consentId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .body(ApiResponse.success(service.reissue(principal.memberId(), planId, consentId)));
    }
}
