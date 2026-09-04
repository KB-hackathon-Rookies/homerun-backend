package com.homerun.domain.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

@Schema(description = "진단용 회원정보 전체 스냅샷. null은 미입력으로 초기화하며 증빙 검증을 뜻하지 않습니다.")
public record UpdateDiagnosisProfileRequest(
        @Past @Schema(description = "생년월일. 오늘 및 미래 날짜 불가", example = "1998-05-14")
        LocalDate birthDate,

        @PositiveOrZero @Max(60) @Schema(description = "사용자가 확인한 복무 개월 수(0~60). 0은 복무 없음, null은 미확인", example = "18")
        Integer militaryMonths) {}
