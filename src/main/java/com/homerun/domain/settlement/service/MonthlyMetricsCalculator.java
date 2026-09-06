package com.homerun.domain.settlement.service;

import com.homerun.domain.settlement.dto.response.MonthlyMetricsResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * 월간 지표 계산(BR-28, FR-H5-03). 순수 산술이라 저장하지 않고 기준 수치도 없다.
 *
 * <p>RIR 안정/위험 컷오프는 공식 출처 미확보(O-3)라 판정하지 않는다 — 숫자만 낸다.
 */
@Service
public class MonthlyMetricsCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE = new BigDecimal("12");

    public MonthlyMetricsResponse calculate(
            long loanAmount, BigDecimal annualRatePercent, long managementFee, long monthlyIncome, long livingCost) {
        long monthlyInterest = BigDecimal.valueOf(loanAmount)
                .multiply(annualRatePercent.divide(HUNDRED, 10, RoundingMode.HALF_UP))
                .divide(TWELVE, 0, RoundingMode.HALF_UP)
                .longValueExact();
        long housingCost = managementFee + monthlyInterest;
        long remaining = monthlyIncome - housingCost - livingCost; // 적자면 음수 그대로

        // 소득이 0이면 RIR 을 계산할 수 없다 — 0으로 나누지 않고 미산출로 둔다.
        BigDecimal rir = monthlyIncome == 0
                ? null
                : BigDecimal.valueOf(housingCost)
                        .multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(monthlyIncome), 1, RoundingMode.HALF_UP);
        return new MonthlyMetricsResponse(monthlyInterest, housingCost, remaining, rir, false);
    }
}
