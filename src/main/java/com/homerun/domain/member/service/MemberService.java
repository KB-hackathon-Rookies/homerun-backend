package com.homerun.domain.member.service;

import com.homerun.domain.auth.service.RefreshTokenService;
import com.homerun.domain.member.dto.request.UpdateMemberProfileRequest;
import com.homerun.domain.member.dto.response.MemberProfileResponse;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final RefreshTokenService refreshTokenService;

    public MemberService(MemberRepository memberRepository, RefreshTokenService refreshTokenService) {
        this.memberRepository = memberRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public MemberProfileResponse getMyProfile(Long memberId) {
        return MemberProfileResponse.from(findActiveMember(memberId));
    }

    @Transactional
    public MemberProfileResponse updateMyProfile(Long memberId, UpdateMemberProfileRequest request) {
        Member member = findActiveMember(memberId);
        member.updateNickname(request.nickname().trim());
        return MemberProfileResponse.from(member);
    }

    @Transactional
    public void withdraw(Long memberId) {
        Member member = findActiveMember(memberId);
        refreshTokenService.revokeAll(memberId);
        member.withdraw();
    }

    private Member findActiveMember(Long memberId) {
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.requireActive();
        return member;
    }
}
