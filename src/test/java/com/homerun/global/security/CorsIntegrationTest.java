package com.homerun.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 프론트가 실제로 붙을 수 있는지 본다.
 *
 * <p>리프레시 토큰이 httpOnly 쿠키라 프론트는 자격 증명을 실어 보낸다. 그러면 서버가
 * {@code Access-Control-Allow-Credentials} 를 주고 origin 을 정확히 되돌려 줘야 한다.
 * 와일드카드로는 브라우저가 거부한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CorsIntegrationTest {

    private static final String FRONTEND = "http://localhost:3000";

    private final MockMvc mvc;

    CorsIntegrationTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @DisplayName("preflight 는 인증 없이 통과한다")
    void should_allow_preflight_without_authentication() throws Exception {
        // preflight 는 Authorization 헤더를 싣지 않는다. 인증을 요구하면 본요청이 나가기도 전에
        // 브라우저가 막는다.
        mvc.perform(options("/api/v1/plans").header("Origin", FRONTEND).header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("허용한 origin 을 그대로 돌려준다")
    void should_echo_allowed_origin() throws Exception {
        mvc.perform(options("/api/v1/plans").header("Origin", FRONTEND).header("Access-Control-Request-Method", "POST"))
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND));
    }

    @Test
    @DisplayName("자격 증명을 허용한다 — 이게 없으면 리프레시 쿠키가 안 실린다")
    void should_allow_credentials() throws Exception {
        mvc.perform(options("/api/v1/plans").header("Origin", FRONTEND).header("Access-Control-Request-Method", "GET"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("허용하지 않은 origin 은 막는다")
    void should_reject_unknown_origin() throws Exception {
        mvc.perform(options("/api/v1/plans")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("프론트가 쓰는 메서드를 전부 허용한다")
    void should_allow_methods_the_frontend_uses() throws Exception {
        for (String method : new String[] {"GET", "POST", "PUT", "PATCH", "DELETE"}) {
            mvc.perform(options("/api/v1/plans")
                            .header("Origin", FRONTEND)
                            .header("Access-Control-Request-Method", method))
                    .andExpect(status().isOk());
        }
    }
}
