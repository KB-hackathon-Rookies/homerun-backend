package com.homerun.domain.auth.controller;

import com.homerun.domain.auth.config.RefreshTokenCookieFactory;
import com.homerun.domain.auth.dto.request.ConfirmEmailVerificationRequest;
import com.homerun.domain.auth.dto.request.EmailLoginRequest;
import com.homerun.domain.auth.dto.request.EmailSignupRequest;
import com.homerun.domain.auth.dto.request.SendEmailVerificationRequest;
import com.homerun.domain.auth.dto.response.EmailVerificationResponse;
import com.homerun.domain.auth.dto.response.LoginResponse;
import com.homerun.domain.auth.service.EmailAuthService;
import com.homerun.domain.auth.service.EmailVerificationService;
import com.homerun.domain.auth.service.OAuthLoginService;
import com.homerun.domain.auth.service.RefreshTokenService;
import com.homerun.domain.member.entity.Member;
import com.homerun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/email")
@Tag(name = "이메일 인증", description = "Redis 인증번호 기반 이메일 회원가입과 로그인")
public class EmailAuthController {

    private final EmailVerificationService verificationService;
    private final EmailAuthService emailAuthService;
    private final RefreshTokenService refreshTokenService;
    private final OAuthLoginService oauthLoginService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    public EmailAuthController(
            EmailVerificationService verificationService,
            EmailAuthService emailAuthService,
            RefreshTokenService refreshTokenService,
            OAuthLoginService oauthLoginService,
            RefreshTokenCookieFactory refreshTokenCookieFactory) {
        this.verificationService = verificationService;
        this.emailAuthService = emailAuthService;
        this.refreshTokenService = refreshTokenService;
        this.oauthLoginService = oauthLoginService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
    }

    @PostMapping("/verification/send")
    @Operation(summary = "회원가입 이메일 인증번호 발송")
    public ApiResponse<Void> sendVerificationCode(@Valid @RequestBody SendEmailVerificationRequest request) {
        verificationService.sendCode(request.email());
        return ApiResponse.success(null);
    }

    @PostMapping("/verification/confirm")
    @Operation(summary = "회원가입 이메일 인증번호 확인")
    public ApiResponse<EmailVerificationResponse> confirmVerificationCode(
            @Valid @RequestBody ConfirmEmailVerificationRequest request) {
        return ApiResponse.success(verificationService.confirmCode(request.email(), request.code()));
    }

    @PostMapping("/signup")
    @Operation(summary = "이메일 회원가입")
    public ResponseEntity<ApiResponse<LoginResponse>> signup(@Valid @RequestBody EmailSignupRequest request) {
        Member member = emailAuthService.signup(
                request.email(), request.password(), request.nickname(), request.verificationToken());
        return loginResponse(member);
    }

    @PostMapping("/login")
    @Operation(summary = "이메일 로그인")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody EmailLoginRequest request) {
        return loginResponse(emailAuthService.login(request.email(), request.password()));
    }

    private ResponseEntity<ApiResponse<LoginResponse>> loginResponse(Member member) {
        String refreshToken = refreshTokenService.issue(member);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieFactory.create(refreshToken).toString())
                .body(ApiResponse.success(oauthLoginService.createLoginResponse(member)));
    }
}
