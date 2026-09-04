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
import com.homerun.domain.policy.model.ConditionResult;
import com.homerun.domain.policy.model.RuleCondition;
import com.homerun.domain.policy.model.RuleDocument;
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
