package com.homerun.domain.property.dto.response;

import com.homerun.domain.property.type.CheckResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 검증 항목 하나의 판정.
 *
 * @param checkCode 항목 코드
 * @param checkLabel 화면에 보여줄 이름
 * @param result 판정
 * @param summary 무엇을 확인했고 결과가 무엇인지
 * @param action 사용자가 지금 할 일. 문제가 없으면 null
 * @param factCode 근거 기준 수치
 * @param sourceUrl 공식 원문
 * @param provisional 기준 수치가 확정 전이라 바뀔 수 있는가
 */
@Schema(description = "매물 검증 항목 결과")
public record CheckFinding(
        String checkCode,
        String checkLabel,
        CheckResult result,
        String summary,
        String action,
        String factCode,
        String sourceUrl,
        boolean provisional) {

    public boolean blocking() {
        return result == CheckResult.BLOCK;
    }
}
