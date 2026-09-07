package com.homerun.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 월간 지표(BR-28, FR-H5-03). 계획/실제 어느 쪽 값을 넣든 같은 공식으로 계산한다.
 * @param monthlyInterest 월 이자(원) = 대출금 × 금리 ÷ 12
 * @param housingCost 월 주거비(원) = 관리비 + 월 이자
 * @param remaining 월 잔여금(원) = 월 소득 − 주거비 − 생활비. 적자면 음수
 */
@Schema(description = "월간 지표(BR-28): 월 이자·주거비·잔여금")
public record MonthlyMetricsResponse(long monthlyInterest, long housingCost, long remaining) {}
