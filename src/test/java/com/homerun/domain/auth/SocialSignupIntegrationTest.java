package com.homerun.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.region.repository.RegionRepository;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.external.sms.SmsSender;
import com.homerun.global.security.jwt.JwtTokenProvider;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * 소셜로 만들어진 계정이 회원가입 트랙을 끝내는 경로.
 *
 * <p>약관 동의 전에도 통과해야 한다 — 가입 트랙이 약관 동의 다음이 아니라 같은 구간에 있어서, 여기가
 * 막히면 신규 소셜 사용자가 어디로도 못 간다.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=social-signup-integration-test-secret-over-32-bytes",
            // 테스트 클래스패스의 application.yaml 이 main 을 가려 만료시간 기본값이 없다. 0 이면
            // 발급하자마자 만료된 토큰이 된다.
            "app.jwt.access-token-expiration-seconds=1800",
            "app.phone-verification.secret=phone-verification-test-secret-over-32-bytes"
        })
class SocialSignupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private SmsSender smsSender;

    @MockitoBean
    private TermsService termsService;

    @BeforeEach
    void clearState() {
        memberRepository.deleteAll();
        Set<String> phoneKeys = redisTemplate.keys("auth:phone:*");
        if (phoneKeys != null && !phoneKeys.isEmpty()) {
            redisTemplate.delete(phoneKeys);
        }
    }

    @Test
    @DisplayName("소셜 신규 회원이 본인 정보를 채우면 가입이 끝난다")
    void should_completeSignup_when_socialMemberFillsProfile() throws Exception {
        Member member =
                memberRepository.save(Member.create(AuthProvider.KAKAO, "kakao-1001", "social@example.com", "홍길동"));
        assertThat(member.isProfileComplete()).isFalse();

        Long regionId = regionRepository.findAll().get(0).getId();
        String phoneToken = completePhoneVerification("010-1234-5678");

        mockMvc.perform(post("/api/v1/auth/social/signup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SocialSignupBody(
                                "홍길동", "1999-05-05", "010-1234-5678", phoneToken, regionId, "101동 1001호"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("KAKAO"));

        Member saved = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(saved.isProfileComplete()).isTrue();
        assertThat(saved.getPhone()).isEqualTo("01012345678"); // 정규화되어 저장
        assertThat(saved.getResidenceRegionId()).isEqualTo(regionId);
    }

    @Test
    @DisplayName("이미 가입이 끝난 계정은 다시 가입할 수 없다")
    void should_reject_when_profileAlreadyComplete() throws Exception {
        Member member =
                memberRepository.save(Member.create(AuthProvider.KAKAO, "kakao-1002", "social2@example.com", "홍길동"));
        Long regionId = regionRepository.findAll().get(0).getId();
        String phoneToken = completePhoneVerification("010-2222-3333");
        member.completeSocialProfile("홍길동", java.time.LocalDate.of(1999, 5, 5), "01022223333", regionId, null);
        memberRepository.saveAndFlush(member);

        mockMvc.perform(post("/api/v1/auth/social/signup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SocialSignupBody(
                                "홍길동", "1999-05-05", "010-2222-3333", phoneToken, regionId, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_034"));
    }

    private String completePhoneVerification(String rawPhone) throws Exception {
        mockMvc.perform(post("/api/v1/auth/phone/verification/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + rawPhone + "\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(smsSender).sendVerificationCode(eq(rawPhone.replaceAll("\\D", "")), codeCaptor.capture(), anyLong());
        String confirmBody = mockMvc.perform(post("/api/v1/auth/phone/verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + rawPhone + "\",\"code\":\"" + codeCaptor.getValue() + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper
                .readTree(confirmBody)
                .path("data")
                .path("verificationToken")
                .asText();
    }

    private record SocialSignupBody(
            String name,
            String birthDate,
            String phone,
            String phoneVerificationToken,
            Long regionId,
            String detailAddress) {}
}
