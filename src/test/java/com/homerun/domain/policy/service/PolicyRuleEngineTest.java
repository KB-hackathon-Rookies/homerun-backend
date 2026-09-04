package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.fact.exception.UnusableFactException;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.fact.type.Confidence;
import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.policy.model.AmountSpec;
import com.homerun.domain.policy.model.ConditionResult;
import com.homerun.domain.policy.model.ExpectedEstimate;
import com.homerun.domain.policy.model.RateSpec;
import com.homerun.domain.policy.model.RuleCondition;
import com.homerun.domain.policy.model.RuleDocument;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import com.homerun.domain.property.entity.Property;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 룰엔진 조건 평가만 검증한다. plan_input ↔ rule_json 매핑이 핵심이라 경계값을 붙인다
 * (연령 상한은 특히 &lt; 와 &lt;= 를 틀리기 쉽다).
 */
class PolicyRuleEngineTest {

    private static final Long PLAN_ID = 1L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC);

    private final FactRegistry facts = mock(FactRegistry.class);
    private final PolicyRuleEngine engine = new PolicyRuleEngine(facts, CLOCK);

    @Test
    void should_returnAllMet_when_everyConditionSatisfied() {
        when(facts.require("FCT-003")).thenReturn(fact("FCT-003", "50000000"));
        RuleDocument document = new RuleDocument(
                "AND",
                List.of(
                        new RuleCondition("HOUSEHOLD_HOMELESS", "household_homeless", "eq", true, null, null),
                        new RuleCondition("INCOME_CAP", "monthly_income", "annual_lte", null, "FCT-003", null)));
        PlanInput input = input(true, null, 4_000_000L, null, null);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results).extracting(ConditionResult::isMet).containsExactly(true, true);
    }

    @Test
    void should_returnNotMet_when_booleanConditionFails() {
        RuleDocument document = new RuleDocument(
                "AND", List.of(new RuleCondition("HOUSEHOLD_HOMELESS", "household_homeless", "eq", true, null, null)));
        PlanInput input = input(false, null, null, null, null);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isFalse();
    }

    @Test
    void should_returnNeedInfo_when_fieldIsMissing() {
        RuleDocument document = new RuleDocument(
                "AND", List.of(new RuleCondition("HOUSEHOLD_HOMELESS", "household_homeless", "eq", true, null, null)));
        PlanInput input = input(null, null, null, null, null);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isNull();
    }

    @Test
    void should_returnNeedInfo_when_factConfidenceIsConflict() {
        when(facts.require("FCT-004")).thenThrow(new UnusableFactException("FCT-004", Confidence.CONFLICT));
        RuleDocument document = new RuleDocument(
                "AND", List.of(new RuleCondition("NET_ASSET_CAP", "net_assets", "lte", null, "FCT-004", null)));
        PlanInput input = input(null, 100_000_000L, null, null, null);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isNull();
    }

    @Test
    void should_returnNeedInfo_when_operatorIsUnsupported() {
        RuleDocument document = new RuleDocument(
                "AND", List.of(new RuleCondition("HOUSING_REQUIREMENT", null, "external_check", null, null, null)));
        PlanInput input = input(null, null, null, null, null);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isNull();
    }

    @Test
    void should_beEligible_when_exactlyOneDayBeforeAgeCutoff() {
        when(facts.require("FCT-174")).thenReturn(fact("FCT-174", "34"));
        // 1991-09-05 생일 → 만 35세 생일 전날인 2026-09-04(clock 과 같은 날)까지는 충족.
        RuleDocument document = ageDocument();
        PlanInput input = input(null, null, null, LocalDate.of(1991, 9, 5), 0);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isTrue();
    }

    @Test
    void should_beIneligible_when_exactlyOnAgeCutoffDate() {
        when(facts.require("FCT-174")).thenReturn(fact("FCT-174", "34"));
        // 1991-09-04 생일 → 오늘(clock)이 정확히 만 35세 생일 당일이라 상한을 넘겼다.
        RuleDocument document = ageDocument();
        PlanInput input = input(null, null, null, LocalDate.of(1991, 9, 4), 0);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isFalse();
    }

    @Test
    void should_capAtAge40_when_militaryAdjustmentWouldExceedFct002Limit() {
        when(facts.require("FCT-174")).thenReturn(fact("FCT-174", "34"));
        // FCT-002: 병역 보정은 최대 만 39세까지다. 60개월(5년)은 정확히 35세→40세 구간과 같아서
        // 캡이 있든 없든 결과가 같다 — 캡이 실제로 결과를 바꾸는지 보려면 그보다 더 커야 한다.
        // 72개월을 그대로 더하면(캡 없이) 만 40세를 넘겨 2027-09-04까지 통과로 잘못 나오는데,
        // 캡을 걸면 2026-09-04(만 40세 생일)에서 끊겨 오늘 기준으로 불충족이 된다.
        RuleDocument document = ageDocument();
        PlanInput input = input(null, null, null, LocalDate.of(1986, 9, 4), 72);

        List<ConditionResult> results = engine.evaluate(document, input);

        // 캡이 없다면 true 가 나와야 정상인데(2027-09-04 까지 유효), 캡을 걸면 2026-09-04 에서
        // 끊겨 오늘(clock)에 이미 불충족이다.
        assertThat(results.get(0).isMet()).isFalse();
    }

    @Test
    void should_beMet_when_depositIsExactlyAtPriceRatioThreshold() {
        when(facts.require("FCT-054")).thenReturn(fact("FCT-054", "1.26"));
        // 공시가 5억 × 1.26 = 6억 3천 = 보증금과 정확히 같음 → <= 조건이라 충족.
        RuleDocument document = priceRatioDocument();
        PlanInput input = input(null, null, null, null, null);
        Property property = property(630_000_000L, 500_000_000L);

        List<ConditionResult> results = engine.evaluate(document, input, property);

        assertThat(results.get(0).isMet()).isTrue();
    }

    @Test
    void should_beNotMet_when_depositExceedsPriceRatioThresholdByOneWon() {
        when(facts.require("FCT-054")).thenReturn(fact("FCT-054", "1.26"));
        RuleDocument document = priceRatioDocument();
        PlanInput input = input(null, null, null, null, null);
        Property property = property(630_000_001L, 500_000_000L);

        List<ConditionResult> results = engine.evaluate(document, input, property);

        assertThat(results.get(0).isMet()).isFalse();
    }

    @Test
    void should_returnNeedInfo_when_propertyIsAbsentForPropertyBasedCondition() {
        RuleDocument document = priceRatioDocument();
        PlanInput input = input(null, null, null, null, null);

        // 2-arg evaluate 는 property 를 아예 안 넘긴다 — plan_input 전용 정책이 property
        // 조건을 잘못 참조해도 NEED_INFO 로 안전하게 떨어져야 한다.
        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isNull();
    }

    @Test
    void should_failFundLoan_when_propertyIsViolationBuilding() {
        // CLAUDE.md: "근생빌라는 대출·보증 둘 다 거절" — 위반건축물이면 기금대출도 막힌다(#100).
        RuleDocument document = houseConditionDocument();
        PlanInput input = input(null, null, null, null, null);
        Property property = houseConditionProperty(true, false);

        List<ConditionResult> results = engine.evaluate(document, input, property);

        assertThat(results)
                .filteredOn(r -> r.code().equals("NOT_VIOLATION_BUILDING"))
                .extracting(ConditionResult::isMet)
                .containsExactly(false);
    }

    @Test
    void should_failFundLoan_when_propertyIsMultiHousehold() {
        // CLAUDE.md: 다가구는 "전세대출 자체가 안 되는 경우가 많다"(#100).
        RuleDocument document = houseConditionDocument();
        PlanInput input = input(null, null, null, null, null);
        Property property = houseConditionProperty(false, true);

        List<ConditionResult> results = engine.evaluate(document, input, property);

        assertThat(results)
                .filteredOn(r -> r.code().equals("NOT_MULTI_HOUSEHOLD"))
                .extracting(ConditionResult::isMet)
                .containsExactly(false);
    }

    @Test
    void should_passHouseConditions_when_propertyHasNeitherIssue() {
        RuleDocument document = houseConditionDocument();
        PlanInput input = input(null, null, null, null, null);
        Property property = houseConditionProperty(false, false);

        List<ConditionResult> results = engine.evaluate(document, input, property);

        assertThat(results).extracting(ConditionResult::isMet).containsExactly(true, true);
    }

    @Test
    void should_returnNeedInfoForHouseConditions_when_propertyNotYetChosen() {
        // 매물을 아직 안 정했으면(propertyId 없이 판정) 집 조건은 NEED_INFO 로 빠져야지
        // FAIL 로 단정하면 안 된다 — "모르는 것을 통과로 보지 않는다"의 반대 방향 실수다.
        RuleDocument document = houseConditionDocument();
        PlanInput input = input(null, null, null, null, null);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results).extracting(ConditionResult::isMet).containsExactly((Boolean) null, null);
    }

    private RuleDocument houseConditionDocument() {
        return new RuleDocument(
                "AND",
                List.of(
                        new RuleCondition("NOT_VIOLATION_BUILDING", "is_violation_building", "eq", false, null, null),
                        new RuleCondition("NOT_MULTI_HOUSEHOLD", "is_multi_household", "eq", false, null, null)));
    }

    private Property houseConditionProperty(boolean violationBuilding, boolean multiHousehold) {
        return Property.candidate(
                PLAN_ID,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                violationBuilding,
                null,
                multiHousehold,
                null,
                null);
    }

    @Test
    void should_calculateEstimate_when_amountAndRateSpecPresent() {
        // #80(JeonseLoanDiagnosisService)의 원래 테스트값과 동일하게 맞춰서 이관 결과가 같은지 본다.
        when(facts.require("FCT-008")).thenReturn(fact("FCT-008", "80"));
        when(facts.require("FCT-171")).thenReturn(fact("FCT-171", "150000000"));
        when(facts.require("FCT-175")).thenReturn(fact("FCT-175", "300000000"));
        when(facts.require("FCT-172")).thenReturn(fact("FCT-172", "2.2"));
        when(facts.require("FCT-173")).thenReturn(fact("FCT-173", "3.3"));
        RuleDocument document = estimateDocument();
        List<ConditionResult> conditions =
                List.of(new ConditionResult("DEPOSIT_CAP", "임차보증금 상한", "3억원", true, "FCT-175", null));
        PlanInput input = depositInput(200_000_000L, 50_000_000L);

        ExpectedEstimate estimate = engine.estimate(document, conditions, input, PolicyVerdictResult.PASS);

        assertThat(estimate.estimatedLoanAmount()).isEqualTo(150_000_000L); // min(200M*80%=160M, cap 150M)
        assertThat(estimate.ownFundsRequired()).isEqualTo(50_000_000L); // 200M - 150M
        assertThat(estimate.recommendedDepositLimit()).isEqualTo(200_000_000L); // min(300M cap, 50M+150M)
        assertThat(estimate.monthlyInterestMin()).isEqualTo(275_000L); // 150M*2.2/1200
        assertThat(estimate.monthlyInterestMax()).isEqualTo(412_500L); // 150M*3.3/1200
    }

    @Test
    void should_returnEmptyEstimate_when_verdictIsFail() {
        RuleDocument document = estimateDocument();
        PlanInput input = depositInput(200_000_000L, 50_000_000L);

        ExpectedEstimate estimate = engine.estimate(document, List.of(), input, PolicyVerdictResult.FAIL);

        assertThat(estimate.isEmpty()).isTrue();
    }

    @Test
    void should_returnEmptyEstimate_when_documentHasNoAmountSpec() {
        // 일반버팀목·서울시이자지원처럼 amount/rate 스펙이 없는 정책.
        RuleDocument document = new RuleDocument("AND", List.of());
        PlanInput input = depositInput(200_000_000L, 50_000_000L);

        ExpectedEstimate estimate = engine.estimate(document, List.of(), input, PolicyVerdictResult.PASS);

        assertThat(estimate.isEmpty()).isTrue();
    }

    @Test
    void should_returnEmptyEstimate_when_hopeDepositIsMissing() {
        RuleDocument document = estimateDocument();
        PlanInput input = depositInput(null, 50_000_000L);

        ExpectedEstimate estimate = engine.estimate(document, List.of(), input, PolicyVerdictResult.PASS);

        assertThat(estimate.isEmpty()).isTrue();
    }

    private RuleDocument estimateDocument() {
        return new RuleDocument(
                "AND", List.of(), new AmountSpec("FCT-008", "FCT-171"), new RateSpec("FCT-172", "FCT-173"));
    }

    private PlanInput depositInput(Long hopeDeposit, Long availableCash) {
        return PlanInput.create(
                PLAN_ID,
                new PlanInputRequest(
                        hopeDeposit,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        availableCash,
                        null,
                        null,
                        null,
                        true,
                        Set.of()));
    }

    private RuleDocument priceRatioDocument() {
        return new RuleDocument(
                "AND",
                List.of(new RuleCondition(
                        "PRICE_RATIO_126", "official_price", "deposit_lte_price_times_fact", null, "FCT-054", null)));
    }

    private Property property(long deposit, long officialPrice) {
        return Property.candidate(
                PLAN_ID,
                null,
                null,
                null,
                null,
                null,
                deposit,
                null,
                officialPrice,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private RuleDocument ageDocument() {
        return new RuleDocument(
                "AND",
                List.of(new RuleCondition(
                        "AGE_UPPER_BOUND",
                        "birth_date",
                        "age_within_years_adjusted",
                        null,
                        "FCT-174",
                        "military_months")));
    }

    private Fact fact(String code, String number) {
        return new Fact(code, code, new BigDecimal(number), "원", number, "https://example.org/" + code, false);
    }

    private PlanInput input(
            Boolean householdHomeless,
            Long netAssets,
            Long monthlyIncome,
            LocalDate birthDate,
            Integer militaryMonths) {
        return PlanInput.create(
                PLAN_ID,
                new PlanInputRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        householdHomeless,
                        birthDate,
                        militaryMonths,
                        monthlyIncome,
                        netAssets,
                        null,
                        null,
                        null,
                        null,
                        true,
                        Set.of()));
    }
}
