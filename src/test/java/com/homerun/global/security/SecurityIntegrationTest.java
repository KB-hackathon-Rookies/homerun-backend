package com.homerun.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private com.homerun.domain.contract.service.ContractChecklistGuideService checklistGuideService;

    @MockitoBean
    private TermsService termsService;

    @MockitoBean
    private MemberRepository memberRepository;

    @BeforeEach
    void allowRequiredTerms() {
        org.mockito.Mockito.when(memberRepository.findById(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Optional.of(Member.create(AuthProvider.KAKAO, "test-provider", null, "테스트")));
        org.mockito.Mockito.when(termsService.hasAgreedAllRequired(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
    }

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

    @Test
    void should_return403_when_requiredTermsAreNotAgreed() throws Exception {
        org.mockito.Mockito.when(termsService.hasAgreedAllRequired(42L)).thenReturn(false);

        mockMvc.perform(get("/api/v1/test/protected")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(42L, Instant.now().plusSeconds(60))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TERMS_005"));
    }

    @Test
    void should_requireAuthentication_forContractChecklist() throws Exception {
        mockMvc.perform(get("/api/v1/contract-checklist")).andExpect(status().isUnauthorized());
    }

    @Test
    void should_permitRegionOptions_withoutToken() throws Exception {
        // 회원가입은 로그인 전이라 지역 목록을 인증 없이 읽을 수 있어야 한다.
        mockMvc.perform(get("/api/v1/regions/jeonse-options")).andExpect(status().isOk());
    }

    @Test
    void should_permitAddressSearch_withoutToken() throws Exception {
        // keyword 없이 부르면 컨트롤러 단계에서 실패한다. 그 상태 코드가 무엇이든
        // 401(인증 필요)만 아니면, 주소 검색이 로그인 전에도 열려 있음(permitAll)을 증명한다.
        mockMvc.perform(get("/api/v1/addresses/search"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getStatus())
                        .isNotEqualTo(401));
    }

    @Test
    void should_returnChecklistWithoutPlan_andLinkExistingDocuments() throws Exception {
        mockMvc.perform(get("/api/v1/contract-checklist")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(42L, Instant.now().plusSeconds(60))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(8));
        for (var item : checklistGuideService.get(null).items()) {
            if (item.documentCode() != null) {
                org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                                "select count(*) from document_type where code = ?",
                                Integer.class,
                                item.documentCode()))
                        .isEqualTo(1);
            }
        }
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
