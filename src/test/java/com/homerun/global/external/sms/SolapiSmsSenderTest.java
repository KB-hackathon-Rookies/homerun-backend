package com.homerun.global.external.sms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 발송 실패 로그에 남길 번호를 가리는 규칙.
 *
 * <p>어느 번호에서 실패했는지는 구분돼야 하고, 휴대전화 번호가 로그에 그대로 쌓이지는 않아야 한다.
 * 둘 중 하나라도 어기면 이 로그를 넣은 의미가 없어진다.
 */
class SolapiSmsSenderTest {

    @ParameterizedTest
    @DisplayName("앞 세 자리와 뒤 네 자리만 남기고 가운데를 가린다")
    @CsvSource({"01012345678,010****5678", "01098765432,010****5432", "0212345678,021****5678"})
    void should_maskMiddle(String phone, String expected) {
        assertThat(SolapiSmsSender.masked(phone)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("가릴 수 없을 만큼 짧으면 통째로 가린다")
    @NullAndEmptySource
    @ValueSource(strings = {"010", "010123"})
    void should_maskEverything_when_tooShort(String phone) {
        assertThat(SolapiSmsSender.masked(phone)).isEqualTo("***");
    }

    /** 가운데를 가린다고 해 놓고 원본이 그대로 새어 나가면 안 된다. */
    @ParameterizedTest
    @DisplayName("가린 값에는 원본 번호의 가운데가 남지 않는다")
    @ValueSource(strings = {"01012345678", "01098765432"})
    void should_notContainOriginal(String phone) {
        assertThat(SolapiSmsSender.masked(phone)).isNotEqualTo(phone).contains("****");
        assertThat(SolapiSmsSender.masked(phone)).doesNotContain(phone.substring(3, 7));
    }
}
