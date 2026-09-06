package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.settlement.dto.response.MonthlyMetricsResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** BR-28 월간 지표. 공식과 경계(적자·소득 0)만 본다. RIR 컷오프 판정은 하지 않는다(O-3). */
class MonthlyMetricsCalculatorTest {

    private final MonthlyMetricsCalculator calculator = new MonthlyMetricsCalculator();

    private MonthlyMetricsResponse calc(long loan, String rate, long mgmt, long income, long living) {
        return calculator.calculate(loan, new BigDecimal(rate), mgmt, income, living);
    }

    @Test
    void should_matchWorkedExample() {
        // 정하은: 1.44억 × 2.2% ÷ 12 = 26.4만 이자. 주거비 = 5만 + 26.4만 = 31.4만.
        // RIR = 31.4 ÷ 245 = 12.8%. 잔여금 = 245 − 31.4 − 100(생활비) = 113.6만.
        MonthlyMetricsResponse r = calc(144_000_000L, "2.2", 50_000L, 2_450_000L, 1_000_000L);

        assertThat(r.monthlyInterest()).isEqualTo(264_000L);
        assertThat(r.housingCost()).isEqualTo(314_000L);
        assertThat(r.rirPercent()).isEqualByComparingTo("12.8");
        assertThat(r.remaining()).isEqualTo(1_136_000L);
    }

    @Test
    void should_notResolveRirCutoff() {
        // 컷오프 출처 미결(O-3) — RIR 숫자는 주되 판정은 하지 않는다.
        assertThat(calc(144_000_000L, "2.2", 50_000L, 2_450_000L, 1_000_000L).rirCutoffResolved())
                .isFalse();
    }

    @Test
    void should_allowNegativeRemaining_whenDeficit() {
        // 적자면 잔여금 음수 그대로 — 연체 위험 신호다.
        MonthlyMetricsResponse r = calc(144_000_000L, "2.2", 50_000L, 300_000L, 200_000L);
        assertThat(r.remaining()).isEqualTo(300_000L - 314_000L - 200_000L);
        assertThat(r.remaining()).isNegative();
    }

    @Test
    void should_leaveRirNull_whenIncomeIsZero() {
        // 소득 0이면 0으로 나누지 않고 RIR 미산출.
        MonthlyMetricsResponse r = calc(144_000_000L, "2.2", 50_000L, 0L, 0L);
        assertThat(r.rirPercent()).isNull();
        assertThat(r.housingCost()).isEqualTo(314_000L);
    }

    @Test
    void should_countOnlyHousingInRir_notLivingCost() {
        // RIR 은 주거비 기준이다. 생활비를 바꿔도 RIR 은 그대로여야 한다.
        BigDecimal a = calc(144_000_000L, "2.2", 50_000L, 2_450_000L, 500_000L).rirPercent();
        BigDecimal b =
                calc(144_000_000L, "2.2", 50_000L, 2_450_000L, 1_500_000L).rirPercent();
        assertThat(a).isEqualByComparingTo(b);
    }
}
