package com.homerun.global.security.config;

import com.homerun.global.security.filter.JwtAuthenticationFilter;
import com.homerun.global.security.filter.RequiredTermsAgreementFilter;
import com.homerun.global.security.handler.RestAccessDeniedHandler;
import com.homerun.global.security.handler.RestAuthenticationEntryPoint;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final CorsProperties corsProperties;

    public SecurityConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * 교차 출처 요청 허용.
     *
     * <p>리프레시 토큰이 httpOnly 쿠키라 {@code allowCredentials} 가 필요하고, 그러면 origin 을
     * 와일드카드로 둘 수 없다. 프론트가 {@code Set-Cookie} 를 받지 못하면 로그인은 되는데
     * 리프레시가 영영 안 된다.
     *
     * <p>{@code Set-Cookie} 는 단순 응답 헤더가 아니라 노출 대상으로 지정하지 않아도 브라우저가
     * 쿠키 저장소에 넣는다. 프론트가 값을 읽을 일은 없다.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        // preflight 결과를 한 시간 캐시한다. 화면마다 OPTIONS 가 한 번씩 더 나가는 것을 줄인다.
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RequiredTermsAgreementFilter requiredTermsAgreementFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            @Value("${management.metrics-public:false}") boolean metricsPublic)
            throws Exception {
        return http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        // preflight 는 Authorization 헤더를 싣지 않는다. 인증을 요구하면 본요청이
                        // 나가기도 전에 브라우저가 막는다.
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        // 공개 경로 목록은 PublicEndpoints 하나에 둔다. API 문서도 같은 것을 본다.
                        .requestMatchers(PublicEndpoints.DOCS)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, PublicEndpoints.GET)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, PublicEndpoints.POST)
                        .permitAll()
                        // 메트릭은 기본으로 잠근다. 엔드포인트 이름과 JVM 상태가 그대로 드러나
                        // 공개하면 공격면을 알려주는 꼴이 된다. 부하 시험·로컬 관측처럼 스크래퍼가
                        // 같은 호스트에 있을 때만 management.metrics-public=true 로 연다.
                        .requestMatchers(request ->
                                metricsPublic && request.getRequestURI().startsWith("/actuator/prometheus"))
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(requiredTermsAgreementFilter, JwtAuthenticationFilter.class)
                .build();
    }
}
