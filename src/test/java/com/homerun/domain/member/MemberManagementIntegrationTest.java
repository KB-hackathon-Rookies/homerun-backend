package com.homerun.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.repository.RefreshTokenRepository;
import com.homerun.domain.auth.service.RefreshTokenService;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.global.security.jwt.JwtTokenProvider;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=member-management-integration-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
class MemberManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearState() {
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void should_getAndUpdateProfile_when_accessTokenIsValid() throws Exception {
        Member member = saveLocalMember("profile@example.com", "기존닉네임");
        String accessToken = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(member.getId()))
                .andExpect(jsonPath("$.data.provider").value("LOCAL"))
                .andExpect(jsonPath("$.data.nickname").value("기존닉네임"));

        mockMvc.perform(patch("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"  새닉네임  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"));

        Member updated = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(updated.getNickname()).isEqualTo("새닉네임");
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(member.getCreatedAt());
    }

    @Test
    void should_returnFieldError_when_nicknameIsBlank() throws Exception {
        Member member = saveLocalMember("validation@example.com", "기존닉네임");
        String accessToken = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(patch("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("nickname"));
    }

    @Test
    void should_revokeTokensAndRejectLogin_when_memberWithdraws() throws Exception {
        String email = "withdraw@example.com";
        Member member = saveLocalMember(email, "탈퇴회원");
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String refreshToken = refreshTokenService.issue(member);

        mockMvc.perform(delete("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        Member withdrawn = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(withdrawn.getDeletedAt()).isNotNull();
        assertThat(refreshTokenRepository.findAll())
                .allSatisfy(token -> assertThat(token.isUsableAt(Instant.now())).isFalse());

        mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_023"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_007"));

        mockMvc.perform(post("/api/v1/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_023"));
    }

    private Member saveLocalMember(String email, String nickname) {
        return memberRepository.saveAndFlush(
                Member.createLocal(email, passwordEncoder.encode("password123"), nickname));
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
