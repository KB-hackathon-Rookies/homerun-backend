package com.homerun.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("oauth")
public record OAuthProperties(Provider google, Provider kakao) {

    public record Provider(String clientId, String clientSecret, String redirectUri) {}
}
