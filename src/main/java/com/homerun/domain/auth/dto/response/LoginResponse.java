package com.homerun.domain.auth.dto.response;

import com.homerun.domain.member.entity.Member;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, MemberResponse member) {
    public record MemberResponse(Long id, String provider, String email, String name) {
        public static MemberResponse from(Member member) {
            return new MemberResponse(member.getId(), member.getProvider().name(), member.getEmail(), member.getName());
        }
    }
}
