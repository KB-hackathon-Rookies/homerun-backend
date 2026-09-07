package com.homerun.domain.settlement.dto.response;

import com.homerun.domain.settlement.type.DelinquencyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 연체 위험 판단(FR-H5-04·BR-28). 월 잔여금이 적자인지를 기준으로 판단한다.
 *
 * @param status 연체 위험 상태
 * @param remaining 월 잔여금(원). 적자면 음수
 * @param autopayWarning 이자 자동이체가 미등록인가(연체이자 경고, BR-23)
 * @param message 상태 안내 문구
 */
@Schema(description = "연체 위험 판단(FR-H5-04)")
public record DelinquencyRiskResponse(
        DelinquencyStatus status, long remaining, boolean autopayWarning, String message) {}
