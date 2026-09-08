package com.homerun.global.security.config;

/**
 * 인증 없이 부를 수 있는 경로.
 *
 * <p>보안 설정({@link SecurityConfig})과 API 문서(OpenAPI)가 **같은 목록을 본다.** 두 곳에 따로 적으면
 * 반드시 어긋난다 — 실제로 어긋나 있었다. 서버는 통과시키는데 문서에는 자물쇠가 붙어, 아직 계정도
 * 없는 사람이 회원가입에 쓰는 휴대전화 인증을 "액세스 토큰이 필요하다" 고 안내하고 있었다.
 *
 * <p>여기에 경로를 더하면 인증이 풀린다. 더하기 전에 그 경로가 정말 로그인 전에 필요한지 본다.
 */
public final class PublicEndpoints {

    /** 문서와 헬스체크. API 문서에는 나오지 않는다. */
    public static final String[] DOCS = {"/swagger-ui/**", "/v3/api-docs/**", "/actuator/health", "/actuator/health/**"
    };

    /** 소셜 로그인은 브라우저가 주소창째로 이동해 오는 요청이라 헤더를 실을 수 없다. */
    public static final String[] GET = {
        "/api/v1/auth/google/login",
        "/api/v1/auth/google/callback",
        "/api/v1/auth/kakao/login",
        "/api/v1/auth/kakao/callback",
        "/api/v1/open-banking/callback",
        // 회원가입은 로그인 전이라 지역·주소 조회를 인증 없이 쓸 수 있어야 한다.
        "/api/v1/regions/**",
        "/api/v1/addresses/**"
    };

    /**
     * 회원가입과 세션 갱신. 여기 있는 것들은 <b>토큰을 얻기 전에</b> 부르는 경로다.
     *
     * <p>이메일·휴대전화 인증번호가 여기 있는 이유가 그것이다. 가입이 끝나야 토큰이 생기는데 가입하려면
     * 인증번호가 필요하므로, 토큰을 요구하면 아무도 가입할 수 없다.
     */
    public static final String[] POST = {
        "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/email/**", "/api/v1/auth/phone/**"
    };

    private PublicEndpoints() {}
}
