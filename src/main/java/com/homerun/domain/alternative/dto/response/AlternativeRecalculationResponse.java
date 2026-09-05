package com.homerun.domain.alternative.dto.response;

import com.homerun.domain.diagnosis.dto.response.DiagnosisResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 대안 재계산 결과(ALT-01-02).
 *
 * <p>"좋아졌다" 같은 종합 판단을 내리지 않는다 — 부족자금과 시점 중 무엇을 더 치는지는 문서에 없는
 * 가중치라, 정하면 지어내는 것이 된다. 두 결과를 나란히 주고 판단은 화면에 맡긴다.
 *
 * @param current 축을 그대로 두고 같은 비용 가정으로 계산한 기준선
 * @param alternative 축을 바꿔 계산한 결과
 * @param shortfallChange {@code alternative − current} 부족자금. 음수면 부족자금이 줄었다
 * @param possibleDateShiftDays 자금 확보 예상일이 며칠 앞당겨졌는가. 양수면 앞당겨진 것이고,
 *     어느 한쪽이라도 시점을 낼 수 없으면(월 가처분이 0 이하) null 이다
 */
@Schema(description = "대안 재계산 결과. 기준선과 대안을 같은 비용 가정으로 계산해 나란히 준다")
public record AlternativeRecalculationResponse(
        Long planId,
        DiagnosisResponse current,
        DiagnosisResponse alternative,
        long shortfallChange,
        Long possibleDateShiftDays) {

    public static AlternativeRecalculationResponse of(
            Long planId, DiagnosisResponse current, DiagnosisResponse alternative) {
        return new AlternativeRecalculationResponse(
                planId,
                current,
                alternative,
                Math.subtractExact(alternative.shortfall(), current.shortfall()),
                shiftDays(current.possibleDate(), alternative.possibleDate()));
    }

    /** 한쪽이라도 시점이 없으면 며칠 당겨졌는지 말할 수 없다. 0 으로 채우면 "변화 없음"으로 읽힌다. */
    private static Long shiftDays(LocalDate current, LocalDate alternative) {
        if (current == null || alternative == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(alternative, current);
    }
}
