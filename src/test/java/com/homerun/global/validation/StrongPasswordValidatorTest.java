package com.homerun.global.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @ParameterizedTest
    @DisplayName("8자 이상, ASCII 영문·숫자·특수문자를 모두 포함하면 통과한다")
    @ValueSource(strings = {"Password123!", "a1!aaaaa", "Abcd123!"})
    void should_accept_strongPassword(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("길이 미달·구성 누락·공백 포함이면 거부한다")
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

    @Test
    @DisplayName("ASCII(33~126)가 아닌 문자가 섞이면 거부한다 — 프론트와 같은 집합")
    void should_reject_nonAsciiCharacters() {
        // 한글
        assertThat(validator.isValid("홍길동pw1@서울", null)).isFalse();
        // 제어문자 U+001C (트리키한 문자는 코드로 만든다 — 소스에 리터럴로 두면 안 보인다)
        assertThat(validator.isValid("Abc123!" + (char) 0x1C, null)).isFalse();
        // 비분리공백 U+00A0
        assertThat(validator.isValid("Abc123!" + (char) 0xA0, null)).isFalse();
        // 서로게이트 𐐀 (U+10400)
        assertThat(validator.isValid("Abc123!" + new String(Character.toChars(0x10400)), null))
                .isFalse();
    }

    @Test
    @DisplayName("null 은 거부한다")
    void should_reject_null() {
        assertThat(validator.isValid(null, null)).isFalse();
    }
}
