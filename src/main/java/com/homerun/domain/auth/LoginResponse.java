package com.homerun.domain.auth;

import com.homerun.domain.member.Member;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, MemberResponse member) {
    public record MemberResponse(Long id, String provider, String email, String nickname) {
        static MemberResponse from(Member member) {
            return new MemberResponse(
                    member.getId(), member.getProvider().name(), member.getEmail(), member.getNickname());
        }
    }
}
