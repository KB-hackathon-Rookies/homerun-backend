package com.homerun.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 로그인 설정.
 *
 * <p>{@code frontendRedirectUri} 는 콜백이 끝난 뒤 브라우저를 돌려보낼 프론트 주소다. 콜백은 사용자가
 * 주소창째로 이동해 온 요청이라 JSON 을 돌려주면 화면에 그대로 찍힌다.
 */
@ConfigurationProperties("oauth")
public record OAuthProperties(Provider google, Provider kakao, String frontendRedirectUri) {

    public record Provider(String clientId, String clientSecret, String redirectUri) {}
}
