package com.homerun.domain.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth-cookie")
public record AuthCookieProperties(boolean secure) {}
