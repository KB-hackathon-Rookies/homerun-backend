package com.homerun.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
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
    private final JwtTokenService jwtTokenService;
    private final RestClient restClient;

    public OAuthLoginService(
            OAuthProperties properties,
            ObjectMapper objectMapper,
            MemberRepository memberRepository,
            JwtTokenService jwtTokenService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.memberRepository = memberRepository;
        this.jwtTokenService = jwtTokenService;
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
        String token = jwtTokenService.createAccessToken(member);
        return new LoginResponse(
                token, "Bearer", propertiesAccessTokenExpirationSeconds(), LoginResponse.MemberResponse.from(member));
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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "소셜 로그인 토큰 발급에 실패했습니다.", exception);
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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, label + " 조회에 실패했습니다.", exception);
        }
    }

    private JsonNode readJson(String response, String label) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, label + " 응답을 해석하지 못했습니다.", exception);
        }
    }

    private OAuthProperties.Provider configuredProvider(OAuthProperties.Provider provider, String providerName) {
        if (provider == null
                || isBlank(provider.clientId())
                || isBlank(provider.clientSecret())
                || isBlank(provider.redirectUri())) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, providerName + " OAuth 환경변수가 설정되지 않았습니다.");
        }
        return provider;
    }

    private String requiredText(JsonNode node, String field, String label) {
        String value = optionalText(node, field);
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, label + "에 " + field + " 값이 없습니다.");
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private long propertiesAccessTokenExpirationSeconds() {
        // JWT 응답의 만료 시간은 JwtTokenService와 같은 설정을 사용한다.
        return jwtTokenService.accessTokenExpirationSeconds();
    }
}
