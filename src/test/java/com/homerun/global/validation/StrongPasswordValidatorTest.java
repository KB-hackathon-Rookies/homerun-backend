package com.homerun.global.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @ParameterizedTest
    @DisplayName("8자 이상 영문·숫자·특수문자를 모두 포함하면 통과한다")
    @ValueSource(strings = {"Password123!", "a1!aaaaa", "홍길동pw1@서울"})
    void should_accept_strongPassword(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("길이 미달·구성 누락·공백 포함·null 이면 거부한다")
    @ValueSource(
            strings = {
                "Pw1!", // 8자 미만
                "password123", // 특수문자 없음
                "password!!!", // 숫자 없음
                "12345678!", // 영문 없음
                "Password 1!" // 공백 포함
            })
    void should_reject_weakPassword(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("null 은 거부한다")
    void should_reject_null() {
        assertThat(validator.isValid(null, null)).isFalse();
    }
}
