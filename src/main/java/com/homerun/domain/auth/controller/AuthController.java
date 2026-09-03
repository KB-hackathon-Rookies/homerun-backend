package com.homerun.domain.auth.controller;

import com.homerun.domain.auth.config.OAuthProperties;
import com.homerun.domain.auth.config.RefreshTokenCookieFactory;
import com.homerun.domain.auth.dto.response.LoginResponse;
import com.homerun.domain.auth.service.OAuthLoginService;
import com.homerun.domain.auth.service.RefreshTokenService;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "인증", description = "Google·Kakao OAuth 로그인")
public class AuthController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OAuthProperties properties;
    private final OAuthLoginService oauthLoginService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final MemberRepository memberRepository;

    public AuthController(
            OAuthProperties properties,
            OAuthLoginService oauthLoginService,
            RefreshTokenService refreshTokenService,
            RefreshTokenCookieFactory refreshTokenCookieFactory,
            MemberRepository memberRepository) {
        this.properties = properties;
        this.oauthLoginService = oauthLoginService;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.memberRepository = memberRepository;
    }

    @GetMapping("/google/login")
    @Operation(summary = "Google 로그인 시작")
    public ResponseEntity<Void> startGoogleLogin(HttpSession session) {
        OAuthProperties.Provider google = requireProvider(properties.google(), "Google");
        String state = createState(session, AuthProvider.GOOGLE);
        URI location = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", google.clientId())
                .queryParam("redirect_uri", google.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .build()
                .encode()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @GetMapping("/kakao/login")
    @Operation(summary = "Kakao 로그인 시작")
    public ResponseEntity<Void> startKakaoLogin(HttpSession session) {
        OAuthProperties.Provider kakao = requireProvider(properties.kakao(), "Kakao");
        String state = createState(session, AuthProvider.KAKAO);
        URI location = UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
                .queryParam("client_id", kakao.clientId())
                .queryParam("redirect_uri", kakao.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .build()
                .encode()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @GetMapping("/google/callback")
    @Operation(summary = "Google 로그인 콜백")
    public ResponseEntity<ApiResponse<LoginResponse>> googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpSession session) {
        validateCallback(AuthProvider.GOOGLE, code, state, error, session);
        return loginResponse(oauthLoginService.login(AuthProvider.GOOGLE, code));
    }

    @GetMapping("/kakao/callback")
    @Operation(summary = "Kakao 로그인 콜백")
    public ResponseEntity<ApiResponse<LoginResponse>> kakaoCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpSession session) {
        validateCallback(AuthProvider.KAKAO, code, state, error, session);
        return loginResponse(oauthLoginService.login(AuthProvider.KAKAO, code));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Access Token 갱신", description = "Refresh Token 쿠키를 회전하고 새 Access Token을 발급합니다.")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (isBlank(refreshToken)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REQUIRED);
        }
        return loginResponse(refreshTokenService.rotate(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "Refresh Token을 폐기하고 브라우저 쿠키를 제거합니다.")
    public ResponseEntity<Void> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken) {
        refreshTokenService.revoke(refreshToken);
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieFactory.expire().toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(summary = "현재 로그인 사용자 조회")
    public ApiResponse<LoginResponse.MemberResponse> me(@AuthenticationPrincipal MemberPrincipal principal) {
        Member member = memberRepository
                .findById(principal.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.requireActive();
        return ApiResponse.success(LoginResponse.MemberResponse.from(member));
    }

    private OAuthProperties.Provider requireProvider(OAuthProperties.Provider provider, String providerName) {
        if (provider == null
                || isBlank(provider.clientId())
                || isBlank(provider.clientSecret())
                || isBlank(provider.redirectUri())) {
            throw new BusinessException(ErrorCode.OAUTH_CONFIGURATION_ERROR);
        }
        return provider;
    }

    private String createState(HttpSession session, AuthProvider provider) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(stateKey(provider), state);
        return state;
    }

    private void validateCallback(AuthProvider provider, String code, String state, String error, HttpSession session) {
        if (!isBlank(error)) {
            throw new BusinessException(ErrorCode.OAUTH_LOGIN_REJECTED);
        }
        String expectedState = (String) session.getAttribute(stateKey(provider));
        session.removeAttribute(stateKey(provider));
        if (isBlank(code) || isBlank(state) || !state.equals(expectedState)) {
            throw new BusinessException(ErrorCode.INVALID_OAUTH_REQUEST);
        }
    }

    private ResponseEntity<ApiResponse<LoginResponse>> loginResponse(Member member) {
        String refreshToken = refreshTokenService.issue(member);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieFactory.create(refreshToken).toString())
                .body(ApiResponse.success(oauthLoginService.createLoginResponse(member)));
    }

    private String stateKey(AuthProvider provider) {
        return "oauth.state." + provider.name();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
