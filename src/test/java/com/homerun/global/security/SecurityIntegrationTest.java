package com.homerun.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import com.homerun.global.response.ApiResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Import({TestcontainersConfiguration.class, SecurityIntegrationTest.ProtectedTestController.class})
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=security-integration-test-secret-must-be-at-least-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
class SecurityIntegrationTest {

    private static final String SECRET = "security-integration-test-secret-must-be-at-least-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_return401_when_accessTokenIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    void should_return401_when_accessTokenIsExpired() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(1L, Instant.now().minusSeconds(1))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    @Test
    void should_return401_when_accessTokenIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void should_returnPrincipal_when_accessTokenIsValid() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(42L, Instant.now().plusSeconds(60))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(42));
    }

    private String token(Long memberId, Instant expiresAt) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(memberId.toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(10)))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    @RestController
    static class ProtectedTestController {

        @GetMapping("/api/v1/test/protected")
        ApiResponse<Long> protectedEndpoint(@AuthenticationPrincipal MemberPrincipal principal) {
            return ApiResponse.success(principal.memberId());
        }
    }
}
