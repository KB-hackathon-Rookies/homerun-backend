package com.homerun.domain.plan.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "진단 입력 초깃값 제안. 조회는 저장하지 않으며 사용자 확인 후 기존 입력 저장 API로 제출해야 합니다.")
public record PlanProfilePrefillResponse(
        @Schema(description = "계획 ID") Long planId,
        @Schema(description = "생년월일 제안값") PrefillValue<LocalDate> birthDate,
        @Schema(description = "병역기간 제안값") PrefillValue<Integer> militaryMonths) {
    public enum Source {
        SAVED_INPUT,
        MEMBER_PROFILE,
        EXPLICIT_UNKNOWN,
        UNAVAILABLE
    }

    @Schema(description = "값과 출처. EXPLICIT_UNKNOWN은 모름 선택 보존, UNAVAILABLE은 미입력입니다. 출처는 기관 검증 여부가 아닙니다.")
    public record PrefillValue<T>(T value, Source source) {}
}
