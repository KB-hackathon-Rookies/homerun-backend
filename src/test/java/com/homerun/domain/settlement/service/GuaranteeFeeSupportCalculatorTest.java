package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.homerun.domain.fact.exception.FactNotFoundException;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.settlement.dto.response.GuaranteeFeeSupportAmountResponse;
import com.homerun.domain.settlement.type.GuaranteeFeeSupportCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** BR-31 보증료 지원 금액. 지원율(청년 외 90%)·가입일 한도(40/30만)·한도 상한이 핵심이다. */
class GuaranteeFeeSupportCalculatorTest {

    private final FactRegistry facts = mock(FactRegistry.class);
    private final GuaranteeFeeSupportCalculator calculator = new GuaranteeFeeSupportCalculator(facts);

    @BeforeEach
    void setUp() {
        doThrow(new FactNotFoundException("none")).when(facts).require(anyString());
        doThrow(new FactNotFoundException("none")).when(facts).won(anyString());
        won("FCT-242", 400_000L); // 신규 가입 한도
        won("FCT-243", 300_000L); // 기존 가입 한도
        pct("FCT-244", "90"); // 청년 외 지원율
        text("FCT-245", "2025-03-31"); // 기준일
    }

    private void pct(String code, String value) {
        doReturn(new Fact(code, code, new BigDecimal(value), "%", code, null, false))
                .when(facts)
                .require(code);
    }

    private void won(String code, long value) {
        doReturn(new Fact(code, code, BigDecimal.valueOf(value), "원", code, null, false))
                .when(facts)
                .require(code);
        doReturn(value).when(facts).won(code);
    }

    private void text(String code, String value) {
        doReturn(new Fact(code, code, null, null, value, null, false))
                .when(facts)
                .require(code);
    }

    private GuaranteeFeeSupportAmountResponse calc(GuaranteeFeeSupportCategory category, long fee, LocalDate enrolled) {
        return calculator.calculate(category, fee, enrolled);
    }

    @Test
    void should_supportFullAmount_forYouth() {
        // 청년은 전액. 보증료 20만 < 한도 40만 → 20만.
        var r = calc(GuaranteeFeeSupportCategory.YOUTH, 200_000L, LocalDate.of(2026, 5, 1));
        assertThat(r.supportAmount()).isEqualTo(200_000L);
        assertThat(r.supportRatePercent()).isEqualByComparingTo("100");
    }

    @Test
    void should_supportFullAmount_forNewlywed() {
        var r = calc(GuaranteeFeeSupportCategory.NEWLYWED, 200_000L, LocalDate.of(2026, 5, 1));
        assertThat(r.supportAmount()).isEqualTo(200_000L);
    }

    @Test
    void should_supportNinetyPercent_forGeneral() {
        // 청년 외 90%. 보증료 20만 × 90% = 18만.
        var r = calc(GuaranteeFeeSupportCategory.GENERAL, 200_000L, LocalDate.of(2026, 5, 1));
        assertThat(r.supportAmount()).isEqualTo(180_000L);
        assertThat(r.supportRatePercent()).isEqualByComparingTo("90");
    }

    @Test
    void should_capAtNewCeiling_whenFeeIsLarge() {
        // 보증료 50만 전액이지만 신규 가입 한도 40만.
        var r = calc(GuaranteeFeeSupportCategory.YOUTH, 500_000L, LocalDate.of(2026, 5, 1));
        assertThat(r.supportAmount()).isEqualTo(400_000L);
    }

    @Test
    void should_useNewCeiling_onCutoffDateItself() {
        // 기준일 2025-03-31 당일 가입은 신규(40만)다 — 이전이 아니다.
        var r = calc(GuaranteeFeeSupportCategory.YOUTH, 500_000L, LocalDate.of(2025, 3, 31));
        assertThat(r.ceiling()).isEqualTo(400_000L);
    }

    @Test
    void should_useOldCeiling_dayBeforeCutoff() {
        // 하루 전(2025-03-30) 가입은 기존(30만).
        var r = calc(GuaranteeFeeSupportCategory.YOUTH, 500_000L, LocalDate.of(2025, 3, 30));
        assertThat(r.ceiling()).isEqualTo(300_000L);
        assertThat(r.supportAmount()).isEqualTo(300_000L);
    }
}
