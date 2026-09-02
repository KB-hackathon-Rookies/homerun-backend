package com.homerun.domain.member.controller;

import com.homerun.domain.auth.config.AuthCookieProperties;
import com.homerun.domain.member.dto.request.UpdateMemberProfileRequest;
import com.homerun.domain.member.dto.response.MemberProfileResponse;
import com.homerun.domain.member.service.MemberService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me")
@Tag(name = "사용자 정보", description = "로그인 사용자 계정 정보 조회·수정·탈퇴")
public class MemberController {

    private final MemberService memberService;
    private final AuthCookieProperties authCookieProperties;

    public MemberController(MemberService memberService, AuthCookieProperties authCookieProperties) {
        this.memberService = memberService;
        this.authCookieProperties = authCookieProperties;
    }

    @GetMapping
    @Operation(summary = "내 정보 조회")
    public ApiResponse<MemberProfileResponse> getMyProfile(@AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.success(memberService.getMyProfile(principal.memberId()));
    }

    @PatchMapping
    @Operation(summary = "내 정보 수정", description = "현재는 서비스 닉네임을 수정할 수 있습니다.")
    public ApiResponse<MemberProfileResponse> updateMyProfile(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody UpdateMemberProfileRequest request) {
        return ApiResponse.success(memberService.updateMyProfile(principal.memberId(), request));
    }

    @DeleteMapping
    @Operation(summary = "회원 탈퇴", description = "계정을 소프트 삭제하고 발급된 모든 Refresh Token을 폐기합니다.")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal MemberPrincipal principal) {
        memberService.withdraw(principal.memberId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(authCookieProperties.secure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}
