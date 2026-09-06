package com.homerun.domain.member.dto.response;

import com.homerun.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "로그인 사용자 계정 정보")
public record MemberProfileResponse(
        @Schema(description = "회원 ID") Long id,
        @Schema(description = "가입 방식", example = "LOCAL") String provider,
        @Schema(description = "이메일", nullable = true) String email,
        @Schema(description = "이름", nullable = true) String name,
        @Schema(description = "가입 시각") Instant createdAt,
        @Schema(description = "마지막 수정 시각") Instant updatedAt) {

    public static MemberProfileResponse from(Member member) {
        return new MemberProfileResponse(
                member.getId(),
                member.getProvider().name(),
                member.getEmail(),
                member.getName(),
                member.getCreatedAt(),
                member.getUpdatedAt());
    }
}
