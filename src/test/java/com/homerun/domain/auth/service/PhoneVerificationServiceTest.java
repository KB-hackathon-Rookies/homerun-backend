package com.homerun.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
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
}
