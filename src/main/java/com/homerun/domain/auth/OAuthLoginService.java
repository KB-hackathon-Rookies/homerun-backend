package com.homerun.domain.auth;

import com.homerun.domain.member.Member;
import com.homerun.domain.member.MemberRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.security.JwtTokenProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class OAuthLoginService {

    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USER_INFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final OAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RestClient restClient;

    public OAuthLoginService(
            OAuthProperties properties,
            ObjectMapper objectMapper,
            MemberRepository memberRepository,
            JwtTokenProvider jwtTokenProvider) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.memberRepository = memberRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.restClient = RestClient.create();
    }

    @Transactional
    public Member login(AuthProvider provider, String authorizationCode) {
        SocialProfile profile =
                switch (provider) {
                    case GOOGLE -> fetchGoogleProfile(authorizationCode);
                    case KAKAO -> fetchKakaoProfile(authorizationCode);
                };
        return memberRepository
                .findByProviderAndProviderUserId(provider, profile.providerId())
                .orElseGet(() -> memberRepository.save(
                        Member.create(provider, profile.providerId(), profile.email(), profile.nickname())));
    }

    public LoginResponse createLoginResponse(Member member) {
        String token = jwtTokenProvider.createAccessToken(member);
        return new LoginResponse(
                token,
                "Bearer",
                jwtTokenProvider.accessTokenExpirationSeconds(),
                LoginResponse.MemberResponse.from(member));
    }

    private SocialProfile fetchGoogleProfile(String authorizationCode) {
        OAuthProperties.Provider google = configuredProvider(properties.google(), "Google");
        JsonNode token = postForToken(GOOGLE_TOKEN_URL, google, authorizationCode);
        String accessToken = requiredText(token, "access_token", "Google 토큰");
        JsonNode userInfo = getUserInfo(GOOGLE_USER_INFO_URL, accessToken, "Google 사용자 정보");
        return new SocialProfile(
                requiredText(userInfo, "sub", "Google 사용자 정보"),
                optionalText(userInfo, "email"),
                optionalText(userInfo, "name"));
    }

    private SocialProfile fetchKakaoProfile(String authorizationCode) {
        OAuthProperties.Provider kakao = configuredProvider(properties.kakao(), "Kakao");
        JsonNode token = postForToken(KAKAO_TOKEN_URL, kakao, authorizationCode);
        String accessToken = requiredText(token, "access_token", "Kakao 토큰");
        JsonNode userInfo = getUserInfo(KAKAO_USER_INFO_URL, accessToken, "Kakao 사용자 정보");
        return new SocialProfile(
                requiredText(userInfo, "id", "Kakao 사용자 정보"),
                optionalText(userInfo.path("kakao_account"), "email"),
                optionalText(userInfo.path("properties"), "nickname"));
    }

    private JsonNode postForToken(String url, OAuthProperties.Provider provider, String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", provider.clientId());
        form.add("client_secret", provider.clientSecret());
        form.add("redirect_uri", provider.redirectUri());
        form.add("code", authorizationCode);
        try {
            String response = restClient
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return readJson(response, "소셜 토큰");
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR, exception);
        }
    }

    private JsonNode getUserInfo(String url, String accessToken, String label) {
        try {
            String response = restClient
                    .get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            return readJson(response, label);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR, exception);
        }
    }

    private JsonNode readJson(String response, String label) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR, exception);
        }
    }

    private OAuthProperties.Provider configuredProvider(OAuthProperties.Provider provider, String providerName) {
        if (provider == null
                || isBlank(provider.clientId())
                || isBlank(provider.clientSecret())
                || isBlank(provider.redirectUri())) {
            throw new BusinessException(ErrorCode.OAUTH_CONFIGURATION_ERROR);
        }
        return provider;
    }

    private String requiredText(JsonNode node, String field, String label) {
        String value = optionalText(node, field);
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
