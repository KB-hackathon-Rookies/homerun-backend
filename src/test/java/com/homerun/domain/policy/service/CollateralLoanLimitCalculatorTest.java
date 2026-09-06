package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.homerun.domain.fact.exception.FactNotFoundException;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.domain.policy.dto.response.CollateralLoanLimitListResponse;
import com.homerun.domain.policy.dto.response.CollateralLoanLimitResponse;
import com.homerun.domain.policy.model.CollateralType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * BR-19 담보별 한도 계산. 시드가 맞는지는 통합테스트가 본다 — 여기서는 공식과 경계만 본다.
 *
 * <p>스터빙은 doReturn/doThrow ... when(mock) 형태다. 기본 스텁이 예외를 던지므로
 * when(mock.require(...)) 를 쓰면 스텁 등록 그 호출에서 먼저 터진다.
 */
class CollateralLoanLimitCalculatorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-06T00:00:00Z"), ZoneOffset.UTC);

    private final FactRegistry facts = mock(FactRegistry.class);
    private final CollateralLoanLimitCalculator calculator = new CollateralLoanLimitCalculator(facts, CLOCK);

    @BeforeEach
    void setUp() {
        doThrow(new FactNotFoundException("none")).when(facts).require(anyString());
        // 대출보증 한도·비율 팩트를 실제 값으로 채운다.
        stub("FCT-211", "2.22억", new BigDecimal("222000000"));
        stub("FCT-212", "4억", new BigDecimal("400000000"));
        stub("FCT-213", "5억", new BigDecimal("500000000"));
        stub("FCT-214", "90", new BigDecimal("90"));
        stub("FCT-059", "3.5", new BigDecimal("3.5"));
        doReturn(222000000L).when(facts).won("FCT-211");
        doReturn(400000000L).when(facts).won("FCT-212");
        doReturn(500000000L).when(facts).won("FCT-213");
    }

    private void stub(String code, String text, BigDecimal num) {
        doReturn(new Fact(code, code, num, num == null ? null : "원", text, null, false))
                .when(facts)
                .require(code);
    }

    private CollateralLoanLimitResponse of(CollateralLoanLimitListResponse r, CollateralType t) {
        return r.collaterals().stream()
                .filter(c -> c.collateral() == t)
                .findFirst()
                .orElseThrow();
    }

    /** 보증금·소득·나이·혼인만 다르게 넣는다. */
    private PlanInput input(Long deposit, Long monthlyIncome, LocalDate birth, MaritalStatus marital) {
        return PlanInput.create(
                1L,
                new PlanInputRequest(
                        deposit,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        marital,
                        null,
                        null,
                        null,
                        null,
                        birth,
                        null,
                        monthlyIncome,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        Set.of()));
    }

    @Test
    void should_capHfByCollateralMax_when_depositIsHuge() {
        // 보증금 5억 × 0.8 = 4억이지만 HF 상한 2.22억에 막힌다. 소득은 넉넉히.
        var result =
                calculator.calculate(input(500_000_000L, 20_000_000L, LocalDate.of(1990, 1, 1), MaritalStatus.SINGLE));

        assertThat(of(result, CollateralType.HF).limit()).isEqualTo(222_000_000L);
    }

    @Test
    void should_capHfByVerifiedIncome_when_incomeIsLow() {
        // 월 200만 × 12 × 3.5 = 8,400만. 보증금 1.8억 × 0.8 = 1.44억보다 작아 소득에 막힌다.
        var result =
                calculator.calculate(input(180_000_000L, 2_000_000L, LocalDate.of(1990, 1, 1), MaritalStatus.SINGLE));

        var hf = of(result, CollateralType.HF);
        assertThat(hf.limit()).isEqualTo(84_000_000L);
        assertThat(hf.note()).contains("증빙소득");
    }

    @Test
    void should_useHug90Percent_when_under35() {
        // 만 34세: 1.8억 × 0.9 = 1.62억.
        var result =
                calculator.calculate(input(180_000_000L, 5_000_000L, LocalDate.of(1992, 1, 1), MaritalStatus.SINGLE));

        assertThat(of(result, CollateralType.HUG).limit()).isEqualTo(162_000_000L);
    }

    @Test
    void should_useHug80Percent_when_over34AndSingle() {
        // 만 40세 미혼: 90% 미적용 → 1.8억 × 0.8 = 1.44억.
        var result =
                calculator.calculate(input(180_000_000L, 5_000_000L, LocalDate.of(1986, 1, 1), MaritalStatus.SINGLE));

        assertThat(of(result, CollateralType.HUG).limit()).isEqualTo(144_000_000L);
    }

    @Test
    void should_excludeHug90_atExactlyAge35_becauseVeteranAdjustmentDoesNotApply() {
        // 만 35세 경계. 병역 보정을 HUG 90%에 적용하면 안 된다(BR-19) — 34는 되고 35는 안 된다.
        // CLOCK 이 2026-09-06 이라 1991-09-06 생은 정확히 만 35세.
        var age35 =
                calculator.calculate(input(180_000_000L, 5_000_000L, LocalDate.of(1991, 9, 6), MaritalStatus.SINGLE));
        assertThat(of(age35, CollateralType.HUG).limit()).isEqualTo(144_000_000L); // 80%

        // 하루 차이로 아직 만 34세면 90%.
        var age34 =
                calculator.calculate(input(180_000_000L, 5_000_000L, LocalDate.of(1991, 9, 7), MaritalStatus.SINGLE));
        assertThat(of(age34, CollateralType.HUG).limit()).isEqualTo(162_000_000L); // 90%
    }

    @Test
    void should_useHug90Percent_when_marriedRegardlessOfAge() {
        // 신혼은 나이와 무관하게 90%. 만 40세라도 1.62억.
        var result =
                calculator.calculate(input(180_000_000L, 5_000_000L, LocalDate.of(1986, 1, 1), MaritalStatus.MARRIED));

        assertThat(of(result, CollateralType.HUG).limit()).isEqualTo(162_000_000L);
    }

    @Test
    void should_capSgiByCollateralMax() {
        // 7억 × 0.8 = 5.6억이지만 SGI 상한 5억.
        var result =
                calculator.calculate(input(700_000_000L, 30_000_000L, LocalDate.of(1990, 1, 1), MaritalStatus.SINGLE));

        assertThat(of(result, CollateralType.SGI).limit()).isEqualTo(500_000_000L);
    }

    @Test
    void should_reportRangeAcrossCollaterals() {
        var result =
                calculator.calculate(input(180_000_000L, 5_000_000L, LocalDate.of(1992, 1, 1), MaritalStatus.SINGLE));

        // HF 1.44억, HUG 1.62억(90%), SGI 1.44억 → min 1.44억, max 1.62억.
        assertThat(result.minLimit()).isEqualTo(144_000_000L);
        assertThat(result.maxLimit()).isEqualTo(162_000_000L);
    }

    @Test
    void should_leaveHfNull_when_incomeUnknown_butStillComputeHugAndSgi() {
        // 증빙소득을 모르면 HF 는 계산할 수 없다. HUG·SGI 는 보증금만으로 나온다.
        var result = calculator.calculate(input(180_000_000L, null, LocalDate.of(1992, 1, 1), MaritalStatus.SINGLE));

        assertThat(of(result, CollateralType.HF).limit()).isNull();
        assertThat(of(result, CollateralType.HUG).limit()).isEqualTo(162_000_000L);
        assertThat(of(result, CollateralType.SGI).limit()).isEqualTo(144_000_000L);
    }
}
