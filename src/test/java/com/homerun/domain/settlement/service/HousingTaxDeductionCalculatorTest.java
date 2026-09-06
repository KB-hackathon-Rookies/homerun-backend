package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.homerun.domain.fact.exception.FactNotFoundException;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.settlement.dto.response.TaxDeductionResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * BR-29 소득공제. 시드가 맞는지는 통합테스트가 본다 — 여기서는 공식과 상한·한도 경계만 본다.
 */
class HousingTaxDeductionCalculatorTest {

    private final FactRegistry facts = mock(FactRegistry.class);
    private final HousingTaxDeductionCalculator calculator = new HousingTaxDeductionCalculator(facts);

    @BeforeEach
    void setUp() {
        doThrow(new FactNotFoundException("none")).when(facts).require(anyString());
        doThrow(new FactNotFoundException("none")).when(facts).won(anyString());
        pct("FCT-239", "40"); // 공제율
        won("FCT-240", 10_000_000L); // 상환액 상한
        won("FCT-053", 4_000_000L); // 소득공제 한도
        pctProvisional("FCT-241", "16.5"); // 간이세율(가정값)
    }

    private void pct(String code, String value) {
        doReturn(new Fact(code, code, new BigDecimal(value), "%", code, null, false))
                .when(facts)
                .require(code);
    }

    private void pctProvisional(String code, String value) {
        doReturn(new Fact(code, code, new BigDecimal(value), "%", code, null, true))
                .when(facts)
                .require(code);
    }

    private void won(String code, long value) {
        doReturn(new Fact(code, code, BigDecimal.valueOf(value), "원", code, null, false))
                .when(facts)
                .require(code);
        doReturn(value).when(facts).won(code);
    }

    @Test
    void should_matchWorkedExample_forMaturityLumpSum() {
        // 정하은: 1.44억 × 2.2% = 316.8만 이자 → × 40% = 126.72만 공제 → × 16.5% ≈ 20.9만 환급.
        TaxDeductionResponse r = calculator.calculate(144_000_000L, new BigDecimal("2.2"), true, null);

        assertThat(r.annualRepayment()).isEqualTo(3_168_000L);
        assertThat(r.deductionAmount()).isEqualTo(1_267_200L);
        assertThat(r.estimatedRefund()).isEqualTo(209_088L);
        assertThat(r.simplifiedRate()).isTrue();
    }

    @Test
    void should_capRepaymentAtTenMillion() {
        // 상환액이 상한(1천만)을 넘어도 공제 대상 상환액은 1천만까지 → 1천만 × 40% = 400만.
        // 연 이자 1,200만 나오게: 대출 6억 × 2% = 1,200만.
        TaxDeductionResponse r = calculator.calculate(600_000_000L, new BigDecimal("2.0"), true, null);

        assertThat(r.annualRepayment()).isEqualTo(12_000_000L);
        assertThat(r.deductionAmount()).isEqualTo(4_000_000L); // 1천만 × 40% = 400만, 한도와 같음
    }

    @Test
    void should_capDeductionAtFourMillion_independentlyOfRepaymentCap() {
        // 한도 400만이 상한과 별개로 실제로 작동하는지 본다. 상한 1천만 × 40% = 400만이라 기본
        // 시드에서는 둘이 겹쳐 한도가 안 걸린다 — 상한을 2천만으로 올려 상환액 2천만 × 40% = 800만
        // 이 되게 하면 한도 400만이 유일한 제약이 된다.
        won("FCT-240", 20_000_000L);
        TaxDeductionResponse r = calculator.calculate(100_000_000L, new BigDecimal("3.0"), false, 20_000_000L);

        assertThat(r.deductionAmount()).isEqualTo(4_000_000L); // 800만이 아니라 한도 400만
    }

    @Test
    void should_notCapDeduction_when_underFourMillion() {
        // 950만 × 40% = 380만 < 한도 400만 → 그대로.
        TaxDeductionResponse r = calculator.calculate(100_000_000L, new BigDecimal("3.0"), false, 9_500_000L);
        assertThat(r.deductionAmount()).isEqualTo(3_800_000L);
    }

    @Test
    void should_useTypedRepayment_forNonMaturity() {
        // 원리금균등은 대출금×금리가 아니라 입력받은 연 상환액을 쓴다.
        TaxDeductionResponse r = calculator.calculate(100_000_000L, new BigDecimal("3.0"), false, 5_000_000L);

        assertThat(r.annualRepayment()).isEqualTo(5_000_000L);
        assertThat(r.deductionAmount()).isEqualTo(2_000_000L); // 500만 × 40%
    }

    @Test
    void should_returnNulls_when_nonMaturityWithoutRepayment() {
        // 원리금균등인데 상환액을 모르면 공제를 지어내지 않는다.
        TaxDeductionResponse r = calculator.calculate(100_000_000L, new BigDecimal("3.0"), false, null);

        assertThat(r.annualRepayment()).isNull();
        assertThat(r.deductionAmount()).isNull();
        assertThat(r.estimatedRefund()).isNull();
    }
}
