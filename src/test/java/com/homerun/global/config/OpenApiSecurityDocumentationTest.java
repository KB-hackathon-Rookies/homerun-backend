package com.homerun.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.terms.service.TermsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 문서가 실제 인증 요구와 같은 말을 하는지 본다.
 *
 * <p>전에는 갈라져 있었다. 서버는 회원가입용 휴대전화 인증번호 발송을 인증 없이 받는데, 문서에는
 * 전역 {@code bearerAuth} 가 걸려 "액세스 토큰이 필요하다" 고 적혀 있었다. 아직 계정도 없는 사람이
 * 넣을 수 없는 토큰이다.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=openapi-security-documentation-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
class OpenApiSecurityDocumentationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TermsService termsService;

    @Test
    @DisplayName("회원가입 전에 부르는 경로에는 문서에도 자물쇠가 없다")
    void should_documentNoSecurity_when_endpointIsPublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // 빈 배열이어야 전역 상속이 끊긴다. 키가 아예 없으면 전역 bearerAuth 를 물려받는다.
                .andExpect(jsonPath("$.paths['/api/v1/auth/phone/verification/send'].post.security")
                        .isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/phone/verification/confirm'].post.security")
                        .isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email/verification/send'].post.security")
                        .isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/regions/jeonse-options'].get.security")
                        .isEmpty());
    }

    /**
     * 자물쇠를 떼는 쪽만 보면 전부 떼어 버려도 통과한다. 보호돼야 하는 경로에 그대로 남아 있는지를
     * 함께 봐야 이 테스트가 무언가를 검증한다.
     *
     * <p>전역 잠금은 문서 루트에만 적힌다. 그것을 물려받는 오퍼레이션에는 {@code security} 키가 아예
     * 없고, 우리가 푼 경로만 빈 배열을 갖는다. 그래서 "키가 없다" 가 곧 "잠겨 있다" 이다.
     */
    @Test
    @DisplayName("로그인이 필요한 경로에는 문서의 자물쇠가 그대로 남는다")
    void should_keepSecurity_when_endpointRequiresLogin() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/members/me'].get").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/members/me'].get.security").doesNotExist());
    }

    @Test
    @DisplayName("문서와 실제가 같다 — 토큰 없이 인증번호 발송을 불러도 401 이 아니다")
    void should_notRequireToken_when_sendingPhoneCode() throws Exception {
        // 형식이 틀린 번호라 실제 발송 전에 막힌다. 여기서 보려는 것은 인증이지 발송이 아니다.
        mvc.perform(post("/api/v1/auth/phone/verification/send")
                        .contentType("application/json")
                        .content("{\"phone\":\"000\"}"))
                .andExpect(status().isBadRequest());
    }
}
