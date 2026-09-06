package com.homerun.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 월간 지표(BR-28, FR-H5-03). 계획/실제 어느 쪽 값을 넣든 같은 공식으로 계산한다.
 *
 * <p>RIR 안정(≤20%)/위험(>30%) 컷오프는 공식 출처가 없어(O-3) 판정하지 않는다. 숫자만 주고
 * status 는 두지 않는다 — 근거 없는 판정을 내보내지 않는다.
 *
 * @param monthlyInterest 월 이자(원) = 대출금 × 금리 ÷ 12
 * @param housingCost 월 주거비(원) = 관리비 + 월 이자
 * @param remaining 월 잔여금(원) = 월 소득 − 주거비 − 생활비. 적자면 음수
 * @param rirPercent 주거비 ÷ 소득 × 100. 소득이 0이면 null
 * @param rirCutoffResolved RIR 안정/위험 컷오프가 확정됐는가. 현재 미결(O-3)이라 항상 false
 */
@Schema(description = "월간 지표(BR-28). RIR 컷오프는 미결이라 판정하지 않는다")
public record MonthlyMetricsResponse(
        long monthlyInterest, long housingCost, long remaining, BigDecimal rirPercent, boolean rirCutoffResolved) {}
