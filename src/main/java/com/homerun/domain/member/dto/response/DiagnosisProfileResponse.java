package com.homerun.domain.member.dto.response;

import com.homerun.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "사용자 입력 진단용 회원정보. 기관 검증 정보가 아닙니다.")
public record DiagnosisProfileResponse(
        @Schema(description = "생년월일. 미입력은 null") LocalDate birthDate,

        @Schema(description = "복무 개월 수. 기존 미확인 기본값은 null, 명시적 미복무는 0")
        Integer militaryMonths) {
    public static DiagnosisProfileResponse from(Member member) {
        return new DiagnosisProfileResponse(member.getBirthDate(), member.getConfirmedMilitaryMonths());
    }
}
