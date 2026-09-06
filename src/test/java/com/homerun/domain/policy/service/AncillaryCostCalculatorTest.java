package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.homerun.domain.fact.exception.FactNotFoundException;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.policy.dto.response.AncillaryCostResponse;
import com.homerun.domain.policy.dto.response.TotalCostResponse;
import com.homerun.domain.policy.model.CollateralType;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * BR-08a 부대비용·BR-21 총비용·BR-27 중개보수. 시드가 맞는지는 통합테스트가 본다 — 여기서는
 * 공식과 구간 경계만 본다. 양쪽이 같은 계산이면 아무것도 검증하지 않으므로 값을 손으로 박아 둔다.
 */
class AncillaryCostCalculatorTest {

    private final FactRegistry facts = mock(FactRegistry.class);
    private final AncillaryCostCalculator calculator = new AncillaryCostCalculator(facts);

    @BeforeEach
    void setUp() {
        doThrow(new FactNotFoundException("none")).when(facts).require(anyString());
        doThrow(new FactNotFoundException("none")).when(facts).won(anyString());

        // 중개보수 구간(보증금 기준), 서울 조례.
        won("FCT-215", 50_000_000L); // 1구간 상한
        pct("FCT-216", "0.5"); // 1구간 요율
        won("FCT-217", 200_000L); // 1구간 한도
        won("FCT-218", 100_000_000L); // 2구간 상한
        pct("FCT-219", "0.4"); // 2구간 요율
        won("FCT-220", 300_000L); // 2구간 한도
        won("FCT-221", 600_000_000L); // 3구간 상한
        pct("FCT-222", "0.3"); // 3구간 요율
        pct("FCT-223", "10"); // 부가세율

        // 인지세 구간(대출금 기준), 고객 부담분.
        won("FCT-224", 50_000_000L);
        won("FCT-225", 0L);
        won("FCT-226", 100_000_000L);
        won("FCT-227", 35_000L);
        won("FCT-228", 1_000_000_000L);
        won("FCT-229", 75_000L);
        won("FCT-230", 175_000L);

        // 대출보증 보증료율.
        pct("FCT-231", "0.04"); // HF 하한
        pct("FCT-232", "0.18"); // HF 상한
        pct("FCT-233", "0.178"); // HUG 하한
        pct("FCT-234", "0.272"); // HUG 상한
        pct("FCT-235", "0.229"); // SGI 아파트
        pct("FCT-236", "0.260"); // SGI 그 외

        won("FCT-237", 500_000L); // 이사비 기본
        won("FCT-238", 3_000L); // 서류비
    }

    private void won(String code, long value) {
        doReturn(new Fact(code, code, BigDecimal.valueOf(value), "원", code, null, false))
                .when(facts)
                .require(code);
        doReturn(value).when(facts).won(code);
    }

    private void pct(String code, String value) {
        doReturn(new Fact(code, code, new BigDecimal(value), "%", code, null, true))
                .when(facts)
                .require(code);
    }

    // --- 중개보수(BR-27) ---

    @Test
    void should_computeBrokerageInThirdBracket() {
        // 정하은: 보증금 1.8억 → 3구간 0.3% → 54만 × 부가세 1.1 = 59.4만.
        AncillaryCostResponse r = calculator.ancillaryCost(180_000_000L, 144_000_000L, CollateralType.HF, null, null);
        assertThat(r.brokerageFee()).isEqualTo(594_000L);
    }

    @Test
    void should_capBrokerageInFirstBracket() {
        // 4,500만 × 0.5% = 22.5만 > 한도 20만 → 20만 × 1.1 = 22만.
        AncillaryCostResponse r = calculator.ancillaryCost(45_000_000L, 30_000_000L, CollateralType.HF, null, null);
        assertThat(r.brokerageFee()).isEqualTo(220_000L);
    }

    @Test
    void should_useSecondBracket_atExactlyFiftyMillion() {
        // 경계: 5천만은 1구간(미만)이 아니라 2구간이다. 5천만 × 0.4% = 20만 < 한도 30만 → 22만.
        AncillaryCostResponse r = calculator.ancillaryCost(50_000_000L, 30_000_000L, CollateralType.HF, null, null);
        assertThat(r.brokerageFee()).isEqualTo(220_000L);
    }

    @Test
    void should_returnNullBrokerageAndTotal_when_depositOverSixHundredMillion() {
        // 6억 이상은 요율표 미확보 → 중개보수도 합계도 null. 없는 값을 지어내지 않는다.
        AncillaryCostResponse r = calculator.ancillaryCost(600_000_000L, 400_000_000L, CollateralType.HF, null, null);
        assertThat(r.brokerageFee()).isNull();
        assertThat(r.total()).isNull();
    }

    // --- 인지세(BR-08a) 구간 경계 ---

    @Test
    void should_chargeZeroStamp_atFiftyMillionLoan() {
        // 5천만 이하는 비과세. 경계 5천만 정확히도 0.
        AncillaryCostResponse r = calculator.ancillaryCost(180_000_000L, 50_000_000L, CollateralType.HF, null, null);
        assertThat(r.stampDuty()).isZero();
    }

    @Test
    void should_charge35000Stamp_justOverFiftyMillion() {
        AncillaryCostResponse r = calculator.ancillaryCost(180_000_000L, 50_000_001L, CollateralType.HF, null, null);
        assertThat(r.stampDuty()).isEqualTo(35_000L);
    }

    @Test
    void should_charge75000Stamp_justOverOneHundredMillion() {
        AncillaryCostResponse r = calculator.ancillaryCost(180_000_000L, 100_000_001L, CollateralType.HF, null, null);
        assertThat(r.stampDuty()).isEqualTo(75_000L);
    }

    @Test
    void should_charge175000Stamp_overOneBillion() {
        AncillaryCostResponse r = calculator.ancillaryCost(180_000_000L, 1_000_000_001L, CollateralType.HF, null, null);
        assertThat(r.stampDuty()).isEqualTo(175_000L);
    }

    // --- 보증료(BR-08a) ---

    @Test
    void should_useSingleSgiRateForApartment_notEstimated() {
        // SGI 아파트 0.229% 단일 요율 → 추정 아님. 1.44억 × 0.00229 = 329,760.
        AncillaryCostResponse r = calculator.ancillaryCost(180_000_000L, 144_000_000L, CollateralType.SGI, true, null);
        assertThat(r.guaranteeFee()).isEqualTo(329_760L);
        assertThat(r.estimated()).isFalse();
    }

    @Test
    void should_useEtcSgiRate_whenNotApartment() {
        // SGI 그 외 0.260% → 1.44억 × 0.0026 = 374,400.
        AncillaryCostResponse r = calculator.ancillaryCost(180_000_000L, 144_000_000L, CollateralType.SGI, false, null);
        assertThat(r.guaranteeFee()).isEqualTo(374_400L);
    }

    @Test
    void should_useHfMidpointRate_andMarkEstimated() {
        // HF 중간값 (0.04+0.18)/2 = 0.11% → 1.44억 × 0.0011 = 158,400. 범위 중간값이라 추정.
        AncillaryCostResponse r = calculator.ancillaryCost(180_000_000L, 144_000_000L, CollateralType.HF, null, null);
        assertThat(r.guaranteeFee()).isEqualTo(158_400L);
        assertThat(r.estimated()).isTrue();
    }

    @Test
    void should_useOverallMidpoint_whenCollateralUnknown() {
        // 담보 미확정: 전체 범위 (0.04+0.272)/2 = 0.156% → 1.44억 × 0.00156 = 224,640. 추정.
        AncillaryCostResponse r = calculator.ancillaryCost(180_000_000L, 144_000_000L, null, null, null);
        assertThat(r.guaranteeFee()).isEqualTo(224_640L);
        assertThat(r.estimated()).isTrue();
    }

    // --- 이사비·합계 ---

    @Test
    void should_useMovingCostInput_overDefault() {
        AncillaryCostResponse r =
                calculator.ancillaryCost(180_000_000L, 144_000_000L, CollateralType.HF, null, 700_000L);
        assertThat(r.movingCost()).isEqualTo(700_000L);
    }

    @Test
    void should_fallBackToDefaultMovingCost_whenInputNull() {
        AncillaryCostResponse r = calculator.ancillaryCost(180_000_000L, 144_000_000L, CollateralType.HF, null, null);
        assertThat(r.movingCost()).isEqualTo(500_000L);
    }

    @Test
    void should_sumAllComponents() {
        // 중개보수 59.4만 + 인지세 7.5만 + 보증료 15.84만 + 이사비 50만 + 서류비 0.3만.
        AncillaryCostResponse r = calculator.ancillaryCost(180_000_000L, 144_000_000L, CollateralType.HF, null, null);
        assertThat(r.total()).isEqualTo(594_000L + 75_000L + 158_400L + 500_000L + 3_000L);
    }

    // --- 총비용(BR-21) ---

    @Test
    void should_computeYear1TotalCost() {
        // 1.44억, 2.2%, SGI 아파트: 이자 316.8만 + 보증료 32.976만 + 인지세 7.5만.
        TotalCostResponse r = calculator.totalCostYear1(144_000_000L, new BigDecimal("2.2"), CollateralType.SGI, true);
        assertThat(r.annualInterest()).isEqualTo(3_168_000L);
        assertThat(r.guaranteeFee()).isEqualTo(329_760L);
        assertThat(r.stampDuty()).isEqualTo(75_000L);
        assertThat(r.totalYear1()).isEqualTo(3_168_000L + 329_760L + 75_000L);
    }
}
