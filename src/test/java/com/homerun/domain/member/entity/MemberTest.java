package com.homerun.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.auth.type.AuthProvider;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 소셜 콜백이 회원가입 트랙으로 보낼지 로그인으로 보낼지를 이 판정 하나로 가른다. 틀리면 기존 회원이
 * 매번 가입 화면으로 끌려가거나, 신규가 본인 정보 없이 통과한다.
 */
class MemberTest {

    @Test
    @DisplayName("소셜로 막 만들어진 회원은 가입이 끝난 것이 아니다")
    void should_beIncomplete_when_socialMemberJustCreated() {
        Member member = Member.create(AuthProvider.KAKAO, "kakao-1", "user@example.com", "홍길동");

        assertThat(member.isProfileComplete()).isFalse();
    }

    @Test
    @DisplayName("본인 정보를 채우면 가입이 끝난 것으로 본다")
    void should_beComplete_when_socialProfileFilled() {
        Member member = Member.create(AuthProvider.KAKAO, "kakao-1", "user@example.com", "홍길동");

        member.completeSocialProfile("홍길동", LocalDate.of(1999, 5, 5), "01012345678", 1L, "101동 1001호");

        assertThat(member.isProfileComplete()).isTrue();
        assertThat(member.getBirthDate()).isEqualTo(LocalDate.of(1999, 5, 5));
        assertThat(member.getResidenceRegionId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("생년월일만 있고 휴대전화·거주지가 없으면 아직 가입이 끝난 것이 아니다")
    void should_beIncomplete_when_onlyBirthDateFilled() {
        Member member = Member.create(AuthProvider.GOOGLE, "google-1", "user@example.com", "홍길동");

        member.updateDiagnosisProfile(LocalDate.of(1999, 5, 5), 0);

        assertThat(member.isProfileComplete()).isFalse();
    }

    @Test
    @DisplayName("본인 정보 없이 만들어진 이메일 회원도 가입이 끝난 것이 아니다")
    void should_beIncomplete_when_localMemberWithoutProfile() {
        Member member = Member.createLocal("user@example.com", "hash", "홍길동");

        assertThat(member.isProfileComplete()).isFalse();
    }

    @Test
    @DisplayName("본인 정보까지 받은 이메일 가입은 가입이 끝난 것이다")
    void should_beComplete_when_localSignupWithProfile() {
        Member member = Member.createLocal(
                "user@example.com", "hash", "홍길동", LocalDate.of(1999, 5, 5), "01012345678", 1L, null);

        assertThat(member.isProfileComplete()).isTrue();
    }
}
