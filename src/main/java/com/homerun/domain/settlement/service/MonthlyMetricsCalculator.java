package com.homerun.domain.settlement.service;

import com.homerun.domain.settlement.dto.response.MonthlyMetricsResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * 월간 지표 계산(BR-28, FR-H5-03). 순수 산술이라 저장하지 않고 기준 수치도 없다.
 *
 * <p>대출 실행 이후 사용자가 실제로 확인할 수 있는 월 이자·주거비·잔여금만 제공한다.
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

        return new MonthlyMetricsResponse(monthlyInterest, housingCost, remaining);
    }
}
