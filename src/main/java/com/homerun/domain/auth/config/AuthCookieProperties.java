package com.homerun.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth-cookie")
public record AuthCookieProperties(boolean secure, String sameSite) {
    public AuthCookieProperties {
        sameSite = sameSite == null || sameSite.isBlank() ? "Lax" : sameSite;
    }
}
