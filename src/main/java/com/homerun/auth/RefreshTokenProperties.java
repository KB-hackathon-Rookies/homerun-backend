package com.homerun.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.refresh-token")
public record RefreshTokenProperties(long expirationDays) {}
