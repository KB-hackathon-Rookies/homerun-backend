package com.homerun.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.auth.config.PhoneVerificationProperties;
import com.homerun.domain.auth.dto.response.PhoneVerificationSendResponse;
import com.homerun.domain.auth.repository.PhoneVerificationStore;
import com.homerun.domain.auth.repository.PhoneVerificationStore.SendPermit;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.sms.SmsSender;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneVerificationServiceTest {

    // normalize 는 의존성을 쓰지 않으므로 null 로 구성해도 된다.
    private final PhoneVerificationService service = new PhoneVerificationService(null, null, null, null);

    @ParameterizedTest
    @DisplayName("하이픈·공백이 섞여 있어도 숫자만 남겨 하나의 형태로 정규화한다")
    @CsvSource({"010-1234-5678,01012345678", "010 1234 5678,01012345678", "01012345678,01012345678"})
    void should_normalizePhone(String input, String expected) {
        assertThat(service.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("국내 휴대전화 형태가 아니면 형식 오류다")
    @ValueSource(strings = {"021234567", "0212345678", "1012345678", "abc", ""})
    void should_reject_invalidPhone(String input) {
        assertThatThrownBy(() -> service.normalize(input))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PHONE_VERIFICATION_INVALID));
    }

    /**
     * 화면이 남은 시간을 세려면 서버가 값을 줘야 한다. 여기서 설정과 다른 값이 나가면 화면의 타이머가
     * 실제 만료보다 먼저 또는 나중에 끝나고, 사용자는 멀쩡한 번호를 만료로 오해하거나 죽은 번호를
     * 계속 넣게 된다.
     */
    @Test
    @DisplayName("발송 결과의 유효시간과 재발송 대기시간은 설정값을 그대로 따른다")
    void should_returnConfiguredDurations_when_codeSent() {
        PhoneVerificationProperties properties = new PhoneVerificationProperties(
                "phone-verification-test-secret-over-32-bytes",
                null,
                Duration.ofMinutes(3),
                Duration.ofMinutes(10),
                Duration.ofSeconds(45),
                Duration.ofMinutes(30),
                5,
                5);
        PhoneVerificationStore store = mock(PhoneVerificationStore.class);
        MemberRepository members = mock(MemberRepository.class);
        when(members.existsByPhoneAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(store.acquireSendPermit(anyString(), anyInt(), any(), any())).thenReturn(SendPermit.GRANTED);
        PhoneVerificationService sending =
                new PhoneVerificationService(properties, store, mock(SmsSender.class), members);

        PhoneVerificationSendResponse response = sending.sendCode("010-1234-5678");

        // 설정에 적은 3분·45초가 그대로 나와야 한다. 응답끼리 비교하면 아무것도 검증하지 못한다.
        assertThat(response.expiresInSeconds()).isEqualTo(180);
        assertThat(response.resendAvailableInSeconds()).isEqualTo(45);
    }

    /**
     * 전용 시크릿이 비어 있으면 이메일 인증 시크릿으로 대체된다.
     *
     * <p>이 대체를 YAML 의 {@code ${A:${B:}}} 로 하면 안 된다. Spring 의 기본값은 A 가 없을 때만
     * 쓰이고 {@code A=} 처럼 빈 값으로 정의돼 있으면 그 빈 값이 이긴다. {@code .env.example} 이
     * 이 변수를 빈 채로 배포하므로, 그대로 복사한 사람은 전원 인증번호 발송이 500 이 됐다.
     */
    @Test
    @DisplayName("전용 시크릿이 비어 있으면 이메일 인증 시크릿을 쓴다")
    void should_useFallbackSecret_when_dedicatedSecretIsBlank() {
        String email = "email-verification-secret-over-32-bytes-long";

        assertThat(propertiesWithSecrets("", email).secret()).isEqualTo(email);
        assertThat(propertiesWithSecrets(null, email).secret()).isEqualTo(email);
        assertThat(propertiesWithSecrets("   ", email).secret()).isEqualTo(email);
    }

    @Test
    @DisplayName("전용 시크릿이 있으면 그것을 그대로 쓴다")
    void should_keepDedicatedSecret_when_present() {
        String dedicated = "phone-verification-secret-over-32-bytes-long";

        assertThat(propertiesWithSecrets(dedicated, "email-verification-secret-over-32-bytes-long")
                        .secret())
                .isEqualTo(dedicated);
    }

    private PhoneVerificationProperties propertiesWithSecrets(String secret, String fallback) {
        return new PhoneVerificationProperties(secret, fallback, null, null, null, null, 0, 0);
    }

    /** 발송이 실패하면 남은 시간을 말할 자격도 없다. 예외가 그대로 올라와야 한다. */
    @Test
    @DisplayName("이미 가입된 번호면 발송하지 않는다")
    void should_reject_when_phoneAlreadyRegistered() {
        PhoneVerificationProperties properties = new PhoneVerificationProperties(
                "phone-verification-test-secret-over-32-bytes", null, null, null, null, null, 0, 0);
        MemberRepository members = mock(MemberRepository.class);
        when(members.existsByPhoneAndDeletedAtIsNull(anyString())).thenReturn(true);
        PhoneVerificationService sending = new PhoneVerificationService(
                properties, mock(PhoneVerificationStore.class), mock(SmsSender.class), members);

        assertThatThrownBy(() -> sending.sendCode("010-1234-5678"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PHONE_ALREADY_REGISTERED));
    }
}
