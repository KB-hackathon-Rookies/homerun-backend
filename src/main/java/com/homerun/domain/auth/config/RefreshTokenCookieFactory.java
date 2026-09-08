package com.homerun.domain.auth.config;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieFactory {

    private static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthCookieProperties authCookieProperties;
    private final RefreshTokenProperties refreshTokenProperties;

    public RefreshTokenCookieFactory(
            AuthCookieProperties authCookieProperties, RefreshTokenProperties refreshTokenProperties) {
        this.authCookieProperties = authCookieProperties;
        this.refreshTokenProperties = refreshTokenProperties;
    }

    public ResponseCookie create(String refreshToken) {
        return baseCookie(refreshToken)
                .maxAge(Duration.ofDays(refreshTokenProperties.expirationDays()))
                .build();
    }

    public ResponseCookie expire() {
        return baseCookie("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(authCookieProperties.secure())
                .sameSite(authCookieProperties.sameSite())
                .path(COOKIE_PATH);
    }
}
