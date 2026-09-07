package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.settlement.dto.response.MonthlyMetricsResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** BR-28 월간 지표. 월 이자·주거비·잔여금 공식과 적자 경계를 검증한다. */
class MonthlyMetricsCalculatorTest {

    private final MonthlyMetricsCalculator calculator = new MonthlyMetricsCalculator();

    private MonthlyMetricsResponse calc(long loan, String rate, long mgmt, long income, long living) {
        return calculator.calculate(loan, new BigDecimal(rate), mgmt, income, living);
    }

    @Test
    void should_matchWorkedExample() {
        // 정하은: 1.44억 × 2.2% ÷ 12 = 26.4만 이자. 주거비 = 5만 + 26.4만 = 31.4만.
        // 잔여금 = 245 − 31.4 − 100(생활비) = 113.6만.
        MonthlyMetricsResponse r = calc(144_000_000L, "2.2", 50_000L, 2_450_000L, 1_000_000L);

        assertThat(r.monthlyInterest()).isEqualTo(264_000L);
        assertThat(r.housingCost()).isEqualTo(314_000L);
        assertThat(r.remaining()).isEqualTo(1_136_000L);
    }

    @Test
    void should_allowNegativeRemaining_whenDeficit() {
        // 적자면 잔여금 음수 그대로 — 연체 위험 신호다.
        MonthlyMetricsResponse r = calc(144_000_000L, "2.2", 50_000L, 300_000L, 200_000L);
        assertThat(r.remaining()).isEqualTo(300_000L - 314_000L - 200_000L);
        assertThat(r.remaining()).isNegative();
    }
}
