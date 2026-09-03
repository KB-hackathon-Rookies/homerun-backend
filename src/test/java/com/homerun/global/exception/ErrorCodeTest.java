package com.homerun.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 에러 코드 값 자체를 지키는 테스트.
 *
 * <p>코드를 도메인 블록마다 나눠 넣기 시작하면 파일을 위아래로 훑어야 다음 번호를 알 수 있다.
 * 사람이 눈으로 세는 대신 여기서 잡는다.
 */
class ErrorCodeTest {

    @Test
    @DisplayName("같은 코드 문자열을 두 번 쓰지 않는다")
    void should_not_reuse_code_string() {
        var duplicates = Arrays.stream(ErrorCode.values())
                .collect(Collectors.groupingBy(ErrorCode::code, Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(java.util.Map.Entry::getKey)
                .toList();

        assertThat(duplicates).isEmpty();
    }

    @Test
    @DisplayName("코드는 도메인 접두사와 세 자리 번호로 이뤄진다")
    void should_follow_code_format() {
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::code))
                .allMatch(code -> code.matches("[A-Z]+_\\d{3}"));
    }

    @Test
    @DisplayName("메시지는 비어 있지 않다")
    void should_have_message() {
        assertThat(Arrays.stream(ErrorCode.values()))
                .allMatch(errorCode ->
                        errorCode.message() != null && !errorCode.message().isBlank());
    }
}
