package com.homerun.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.service.EmailVerificationService;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.region.repository.RegionRepository;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.mail.VerificationEmailSender;
import com.homerun.global.external.sms.SmsSender;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=email-signup-integration-test-secret-over-32-bytes",
            "app.email-verification.secret=email-verification-test-secret-over-32-bytes",
            "app.email-verification.from=test@homerun.local",
            "app.phone-verification.secret=phone-verification-test-secret-over-32-bytes"
        })
class EmailSignupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationService verificationService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RegionRepository regionRepository;

    @MockitoBean
    private VerificationEmailSender emailSender;

    @MockitoBean
    private SmsSender smsSender;

    @MockitoBean
    private TermsService termsService;

    @BeforeEach
    void clearState() {
        memberRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("auth:email:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        Set<String> phoneKeys = redisTemplate.keys("auth:phone:*");
        if (phoneKeys != null && !phoneKeys.isEmpty()) {
            redisTemplate.delete(phoneKeys);
        }
    }

    @Test
    @DisplayName("이메일·휴대전화 인증을 마치면 본인 정보와 함께 회원가입·로그인이 가능하다")
    void should_signupAndLogin_when_emailAndPhoneVerificationSucceed() throws Exception {
        String email = "NewUser@Example.com";
        String rawPhone = "010-1234-5678";
        String password = "Password123!";
        Long regionId = regionRepository.findAll().get(0).getId();

        String emailToken = completeEmailVerification(email);
        String phoneToken = completePhoneVerification(rawPhone);

        mockMvc.perform(post("/api/v1/auth/email/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupBody(
                                email,
                                password,
                                "홍길동",
                                LocalDate.of(1998, 4, 11),
                                rawPhone,
                                regionId,
                                "테헤란로 123, 101동 101호",
                                emailToken,
                                phoneToken))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(jsonPath("$.data.member.provider").value("LOCAL"))
                .andExpect(jsonPath("$.data.member.email").value("newuser@example.com"));

        Member saved = memberRepository
                .findByProviderAndProviderUserIdAndDeletedAtIsNull(AuthProvider.LOCAL, "newuser@example.com")
                .orElseThrow();
        assertThat(saved.getEmailVerifiedAt()).isNotNull();
        assertThat(saved.getName()).isEqualTo("홍길동");
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(1998, 4, 11));
        assertThat(saved.getPhone()).isEqualTo("01012345678"); // 정규화되어 저장
        assertThat(saved.getPhoneVerifiedAt()).isNotNull();
        assertThat(saved.getResidenceRegionId()).isEqualTo(regionId);
        assertThat(saved.getDetailAddress()).isEqualTo("테헤란로 123, 101동 101호");
        assertThat(passwordEncoder.matches(password, saved.getPasswordHash())).isTrue();

        mockMvc.perform(post("/api/v1/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newuser@example.com\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(jsonPath("$.data.member.id").value(saved.getId()));

        mockMvc.perform(post("/api/v1/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newuser@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_022"));
    }

    @Test
    @DisplayName("휴대전화 인증 토큰이 없으면 회원가입이 거부된다")
    void should_rejectSignup_when_phoneTokenMissing() throws Exception {
        String email = "nophone@example.com";
        Long regionId = regionRepository.findAll().get(0).getId();
        String emailToken = completeEmailVerification(email);

        mockMvc.perform(post("/api/v1/auth/email/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupBody(
                                email,
                                "Password123!",
                                "홍길동",
                                LocalDate.of(1998, 4, 11),
                                "010-2222-3333",
                                regionId,
                                null,
                                emailToken,
                                "  "))))
                .andExpect(status().isBadRequest());
    }

    private String completeEmailVerification(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/email/verification/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationCode(eq(email.toLowerCase()), codeCaptor.capture(), anyLong());
        String confirmBody = mockMvc.perform(post("/api/v1/auth/email/verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + codeCaptor.getValue() + "\"}"))
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

    private String completePhoneVerification(String rawPhone) throws Exception {
        mockMvc.perform(post("/api/v1/auth/phone/verification/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + rawPhone + "\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(smsSender).sendVerificationCode(eq("01012345678"), codeCaptor.capture(), anyLong());
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

    @Test
    @DisplayName("인증번호는 원문이 아닌 해시로 TTL과 함께 Redis에 저장된다")
    void should_storeHashedCodeWithTtl_when_verificationIsRequested() {
        String email = "secure@example.com";
        verificationService.sendCode(email);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationCode(eq(email), codeCaptor.capture(), anyLong());
        Set<String> codeKeys = redisTemplate.keys("auth:email:code:*");

        assertThat(codeKeys).hasSize(1);
        String key = codeKeys.iterator().next();
        assertThat(redisTemplate.opsForValue().get(key)).isNotEqualTo(codeCaptor.getValue());
        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 300L);
    }

    @Test
    @DisplayName("인증번호를 연속으로 틀리면 다섯 번 뒤 인증 요청을 폐기한다")
    void should_discardCode_when_attemptLimitIsExceeded() {
        String email = "attempts@example.com";
        verificationService.sendCode(email);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationCode(eq(email), codeCaptor.capture(), anyLong());
        String code = codeCaptor.getValue();
        String wrongCode = (code.startsWith("0") ? "1" : "0") + code.substring(1);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> verificationService.confirmCode(email, wrongCode))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception ->
                                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_INVALID));
        }
        assertThatThrownBy(() -> verificationService.confirmCode(email, wrongCode))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED));
        assertThat(redisTemplate.keys("auth:email:code:*")).isEmpty();
    }

    @Test
    @DisplayName("인증 메일 재발송은 일 분 동안 제한한다")
    void should_rateLimit_when_codeIsRequestedDuringCooldown() throws Exception {
        String body = "{\"email\":\"cooldown@example.com\"}";

        mockMvc.perform(post("/api/v1/auth/email/verification/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/email/verification/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_017"));
    }

    @Test
    @DisplayName("인증 완료 토큰은 회원가입에 한 번만 사용할 수 있다")
    void should_rejectReuse_when_verifiedTokenWasConsumed() throws Exception {
        String email = "once@example.com";
        String token = "one-time-token";
        redisTemplate.opsForValue().set("auth:email:verified:" + sha256(token), sha256(email));

        verificationService.consumeVerifiedToken(email, token);

        assertThatThrownBy(() -> verificationService.consumeVerifiedToken(email, token))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID));
    }

    private String sha256(String value) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private record SignupBody(
            String email,
            String password,
            String name,
            LocalDate birthDate,
            String phone,
            Long regionId,
            String detailAddress,
            String emailVerificationToken,
            String phoneVerificationToken) {}
}
