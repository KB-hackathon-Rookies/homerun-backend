package com.homerun.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.auth.service.RefreshTokenService;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.dto.request.UpdateMemberProfileRequest;
import com.homerun.domain.member.dto.response.MemberProfileResponse;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    private MemberService memberService;
    private Member member;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, refreshTokenService);
        member = Member.create(AuthProvider.KAKAO, "provider-id", "member@example.com", "기존닉네임");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
    }

    @Test
    void should_returnActiveMemberProfile_when_memberExists() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        MemberProfileResponse response = memberService.getMyProfile(MEMBER_ID);

        assertThat(response.id()).isEqualTo(MEMBER_ID);
        assertThat(response.provider()).isEqualTo("KAKAO");
        assertThat(response.nickname()).isEqualTo("기존닉네임");
    }

    @Test
    void should_trimAndUpdateNickname_when_requestIsValid() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        MemberProfileResponse response =
                memberService.updateMyProfile(MEMBER_ID, new UpdateMemberProfileRequest("  새닉네임  "));

        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(member.getNickname()).isEqualTo("새닉네임");
    }

    @Test
    void should_softDeleteAndRevokeAllRefreshTokens_when_memberWithdraws() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        memberService.withdraw(MEMBER_ID);

        assertThat(member.getDeletedAt()).isNotNull();
        verify(refreshTokenService).revokeAll(MEMBER_ID);
    }

    @Test
    void should_rejectProfileAccess_when_memberAlreadyWithdrew() {
        member.withdraw();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.getMyProfile(MEMBER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEMBER_WITHDRAWN));
    }

    @Test
    void should_throwMemberNotFound_when_memberDoesNotExist() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMyProfile(MEMBER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }
}
