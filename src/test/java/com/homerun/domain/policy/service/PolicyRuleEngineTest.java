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
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.FinancialValueSource;
import com.homerun.domain.plan.type.MaritalStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 룰엔진 조건 평가만 검증한다. plan_input ↔ rule_json 매핑이 핵심이라 경계값을 붙인다
 * (연령 상한은 특히 &lt; 와 &lt;= 를 틀리기 쉽다).
 */
class PolicyRuleEngineTest {

    private static final Long PLAN_ID = 1L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC);

    private final FactRegistry facts = mock(FactRegistry.class);
    private final PolicyRuleEngine engine =
            new PolicyRuleEngine(facts, CLOCK, mock(com.homerun.domain.region.service.PolicyRegionResolver.class));

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
    void should_requireConfirmation_beforeUsingOpenBankingIncomeForEligibility() {
        when(facts.require("FCT-003")).thenReturn(fact("FCT-003", "50000000"));
        RuleDocument document = new RuleDocument(
                "AND", List.of(new RuleCondition("INCOME_CAP", "monthly_income", "annual_lte", null, "FCT-003", null)));
        PlanInput input = input(null, null, 3_000_000L, null, null);
        ReflectionTestUtils.setField(input, "incomeSource", FinancialValueSource.OPEN_BANKING);
        ReflectionTestUtils.setField(input, "financialDataConfirmed", false);

        ConditionResult unconfirmed = engine.evaluate(document, input).get(0);
        ReflectionTestUtils.setField(input, "financialDataConfirmed", true);
        ConditionResult confirmed = engine.evaluate(document, input).get(0);

        assertThat(unconfirmed.isMet()).isNull();
        assertThat(confirmed.isMet()).isTrue();
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
    void should_beMet_when_referenceOnlyFactExists() {
        when(facts.require("FCT-056")).thenReturn(fact("FCT-056", "126"));
        RuleDocument document = new RuleDocument(
                "AND", List.of(new RuleCondition("LIMIT", null, "reference_only", null, "FCT-056", null)));
        PlanInput input = input(null, null, null, null, null);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isTrue();
    }

    @Test
    void should_returnNeedInfo_when_referenceOnlyFactMissing() {
        when(facts.require("FCT-999")).thenThrow(new UnusableFactException("FCT-999", Confidence.UNKNOWN));
        RuleDocument document = new RuleDocument(
                "AND", List.of(new RuleCondition("LIMIT", null, "reference_only", null, "FCT-999", null)));
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
        // POL-01-04: 자격이 사라지는 날짜(만 35세 생일)를 결과에 실어 노출한다.
        assertThat(results.get(0).eligibleUntil()).isEqualTo(LocalDate.of(2026, 9, 5));
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
        // eligibleUntil 도 캡 없는 계산값(2027-09-04)이 아니라 만 40세 하드캡(2026-09-04)이어야 한다.
        assertThat(results.get(0).eligibleUntil()).isEqualTo(LocalDate.of(2026, 9, 4));
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

    // --- in 연산자 (BR-09 주택유형, #190) ---

    @Test
    void should_pass_when_houseTypeIsInAllowedList() {
        List<ConditionResult> results = engine.evaluate(
                new RuleDocument("AND", List.of(houseTypeCondition())), null, typedProperty("OFFICETEL"));

        assertThat(results.get(0).isMet()).isTrue();
    }

    @Test
    void should_fail_when_houseTypeIsNotInAllowedList() {
        List<ConditionResult> results = engine.evaluate(
                new RuleDocument("AND", List.of(houseTypeCondition())), null, typedProperty("DETACHED"));

        assertThat(results.get(0).isMet()).isFalse();
    }

    @Test
    void should_needInfo_when_propertyIsAbsentForHouseTypeCondition() {
        // 매물이 없으면 유형을 모른다. plan_input 의 희망 유형은 값 체계가 달라 대신 쓰지 않는다.
        List<ConditionResult> results = engine.evaluate(
                new RuleDocument("AND", List.of(houseTypeCondition())), input(true, null, null, null, null), null);

        assertThat(results.get(0).isMet()).isNull();
    }

    @Test
    void should_needInfo_when_inConditionValueIsNotAList() {
        // 조건식이 잘못 적혔으면 판정하지 않는다 — 틀린 조건식으로 가능·불가를 단정하지 않는다.
        RuleCondition broken = new RuleCondition("HOUSE_TYPE", "house_type", "in", "APARTMENT", null, null);

        List<ConditionResult> results =
                engine.evaluate(new RuleDocument("AND", List.of(broken)), null, typedProperty("APARTMENT"));

        assertThat(results.get(0).isMet()).isNull();
    }

    private RuleCondition houseTypeCondition() {
        return new RuleCondition(
                "HOUSE_TYPE", "house_type", "in", List.of("APARTMENT", "OFFICETEL", "ROW_HOUSE"), null, null);
    }

    private Property typedProperty(String houseType) {
        return Property.candidate(
                PLAN_ID, null, null, null, null, houseType, null, null, null, null, null, null, null, null, null, null);
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
    void should_beMet_when_areaIsExactlyAtCap() {
        when(facts.require("FCT-005")).thenReturn(fact("FCT-005", "85"));
        RuleDocument document = areaDocument();
        PlanInput input = areaInput(new BigDecimal("85.00"));

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isTrue();
    }

    @Test
    void should_beNotMet_when_areaExceedsCapBySmallestUnit() {
        when(facts.require("FCT-005")).thenReturn(fact("FCT-005", "85"));
        RuleDocument document = areaDocument();
        PlanInput input = areaInput(new BigDecimal("85.01"));

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isFalse();
    }

    @Test
    void should_returnNeedInfo_when_areaIsMissing() {
        RuleDocument document = areaDocument();
        PlanInput input = areaInput(null);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isNull();
    }

    private RuleDocument areaDocument() {
        return new RuleDocument("AND", List.of(new RuleCondition("AREA_CAP", "area_m2", "lte", null, "FCT-005", null)));
    }

    private PlanInput areaInput(BigDecimal areaM2) {
        return PlanInput.create(
                PLAN_ID,
                new PlanInputRequest(
                        null, null, null, null, null, null, areaM2, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, true, Set.of()));
    }

    @Test
    void should_useYouthFactCode_when_singleAndWithinYouthAge() {
        when(facts.require("FCT-176")).thenReturn(fact("FCT-176", "50000000"));
        RuleDocument document = guaranteeFeeDocument();
        // 만 30세, 월 400만(연 4800만) → 청년 상한(연 5000만) 이내.
        PlanInput input = guaranteeFeeInput(MaritalStatus.SINGLE, LocalDate.of(1996, 9, 4), 4_000_000L);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isTrue();
        assertThat(results.get(0).factCode()).isEqualTo("FCT-176"); // 실제로 쓴 fact 를 남긴다
    }

    @Test
    void should_useGeneralFactCode_when_singleAndOutsideYouthAge() {
        when(facts.require("FCT-177")).thenReturn(fact("FCT-177", "60000000"));
        RuleDocument document = guaranteeFeeDocument();
        // 만 45세(청년 범위 밖) → altFactCode(일반, FCT-177)로 비교해야 한다.
        PlanInput input = guaranteeFeeInput(MaritalStatus.SINGLE, LocalDate.of(1981, 9, 4), 5_500_000L);

        List<ConditionResult> results = engine.evaluate(document, input);

        // 연 6600만 > 일반 상한 6000만 → 불충족. 청년 상한(5000만)과 헷갈렸다면 FCT-176 을
        // 찾다가 스텁이 없어 NEED_INFO 로 잘못 떨어졌을 것 — 그게 아니라 FAIL 이어야 한다.
        assertThat(results.get(0).isMet()).isFalse();
        assertThat(results.get(0).factCode()).isEqualTo("FCT-177");
    }

    @Test
    void should_returnNeedInfo_when_married() {
        // 신혼부부(혼인 7년 이내) 여부를 plan_input 이 모른다 — 소득이 아무리 낮아도 단정 못 한다.
        RuleDocument document = guaranteeFeeDocument();
        PlanInput input = guaranteeFeeInput(MaritalStatus.MARRIED, LocalDate.of(1996, 9, 4), 1_000_000L);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isNull();
    }

    @Test
    void should_returnNeedInfo_when_maritalStatusIsMissing() {
        RuleDocument document = guaranteeFeeDocument();
        PlanInput input = guaranteeFeeInput(null, LocalDate.of(1996, 9, 4), 1_000_000L);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isNull();
    }

    private RuleDocument guaranteeFeeDocument() {
        return new RuleDocument(
                "AND",
                List.of(new RuleCondition(
                        "INCOME_CAP_FEE_SUPPORT",
                        "monthly_income",
                        "annual_lte_by_age_group",
                        null,
                        "FCT-176",
                        null,
                        "FCT-177")));
    }

    private PlanInput guaranteeFeeInput(MaritalStatus maritalStatus, LocalDate birthDate, Long monthlyIncome) {
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
                        maritalStatus,
                        null,
                        null,
                        null,
                        null,
                        birthDate,
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
    void should_beEligible_when_exactlyOnMinAgeBirthday() {
        when(facts.require("FCT-179")).thenReturn(fact("FCT-179", "19"));
        when(facts.require("FCT-180")).thenReturn(fact("FCT-180", "34"));
        // 2007-09-04 생일 → 오늘(clock)이 정확히 만 19세 생일 당일 → 하한 충족(포함).
        RuleDocument document = ageRangeDocument();
        PlanInput input = input(null, null, null, LocalDate.of(2007, 9, 4), 0);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isTrue();
    }

    @Test
    void should_beIneligible_when_oneDayBeforeMinAgeBirthday() {
        when(facts.require("FCT-179")).thenReturn(fact("FCT-179", "19"));
        when(facts.require("FCT-180")).thenReturn(fact("FCT-180", "34"));
        // 2007-09-05 생일 → 만 19세 생일 하루 전(clock 기준) → 아직 하한 미달.
        RuleDocument document = ageRangeDocument();
        PlanInput input = input(null, null, null, LocalDate.of(2007, 9, 5), 0);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isFalse();
    }

    @Test
    void should_beIneligible_when_exactlyOnMaxAgeCutoffDate() {
        when(facts.require("FCT-179")).thenReturn(fact("FCT-179", "19"));
        when(facts.require("FCT-180")).thenReturn(fact("FCT-180", "34"));
        // 1991-09-04 생일 → 오늘이 정확히 만 35세 생일 당일 → 상한(34세) 초과.
        RuleDocument document = ageRangeDocument();
        PlanInput input = input(null, null, null, LocalDate.of(1991, 9, 4), 0);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isFalse();
    }

    @Test
    void should_extendMaxAge_when_militaryMonthsAdjust() {
        when(facts.require("FCT-179")).thenReturn(fact("FCT-179", "19"));
        when(facts.require("FCT-180")).thenReturn(fact("FCT-180", "34"));
        // 1991-09-04 생일(캡 없으면 오늘 불충족) + 병역 12개월 → 상한이 1년 늘어 2027-09-04까지
        // 유효 → 오늘(clock) 기준 충족으로 바뀐다.
        RuleDocument document = ageRangeDocument();
        PlanInput input = input(null, null, null, LocalDate.of(1991, 9, 4), 12);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isTrue();
    }

    @Test
    void should_returnEligibleDate_when_stillTooYoungForMinAge() {
        when(facts.require("FCT-179")).thenReturn(fact("FCT-179", "19"));
        // 2008-01-01 생일 → 만 19세는 2027-01-01부터. 오늘(clock=2026-09-04)은 아직 하한 미달.
        RuleCondition condition = ageRangeDocument().conditions().get(0);
        PlanInput input = input(null, null, null, LocalDate.of(2008, 1, 1), 0);

        var gap = engine.upcomingAgeEligibility(condition, input);

        assertThat(gap).isPresent();
        assertThat(gap.get().eligibleFrom()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void should_returnEmpty_when_minAgeAlreadyMet() {
        when(facts.require("FCT-179")).thenReturn(fact("FCT-179", "19"));
        // 2007-09-04 생일 → 오늘이 정확히 만 19세 생일 당일 → 이미 충족이라 큐에 넣을 이유가 없다.
        RuleCondition condition = ageRangeDocument().conditions().get(0);
        PlanInput input = input(null, null, null, LocalDate.of(2007, 9, 4), 0);

        assertThat(engine.upcomingAgeEligibility(condition, input)).isEmpty();
    }

    @Test
    void should_returnEmpty_when_conditionIsNotAgeRangeAdjusted() {
        RuleCondition condition =
                new RuleCondition("INCOME_CAP", "monthly_income", "annual_lte", null, "FCT-003", null);
        PlanInput input = input(null, null, null, LocalDate.of(2008, 1, 1), 0);

        assertThat(engine.upcomingAgeEligibility(condition, input)).isEmpty();
    }

    @Test
    void should_returnEmpty_when_birthDateMissingForAgeGap() {
        RuleCondition condition = ageRangeDocument().conditions().get(0);
        PlanInput input = input(null, null, null, null, 0);

        assertThat(engine.upcomingAgeEligibility(condition, input)).isEmpty();
    }

    @Test
    void should_returnEmpty_when_minAgeFactUnusable() {
        when(facts.require("FCT-179")).thenThrow(new UnusableFactException("FCT-179", Confidence.UNKNOWN));
        RuleCondition condition = ageRangeDocument().conditions().get(0);
        PlanInput input = input(null, null, null, LocalDate.of(2008, 1, 1), 0);

        assertThat(engine.upcomingAgeEligibility(condition, input)).isEmpty();
    }

    private RuleDocument ageRangeDocument() {
        return new RuleDocument(
                "AND",
                List.of(new RuleCondition(
                        "AGE_RANGE",
                        "birth_date",
                        "age_range_adjusted",
                        null,
                        "FCT-179",
                        "military_months",
                        "FCT-180")));
    }

    @Test
    void should_useSalariedFactCode_when_fullTimeEmployee() {
        when(facts.require("FCT-181")).thenReturn(fact("FCT-181", "75000000"));
        RuleDocument document = employmentIncomeDocument();
        PlanInput input = employmentInput(EmploymentType.FULL_TIME, 5_000_000L);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isTrue(); // 연 6000만 <= 7500만
        assertThat(results.get(0).factCode()).isEqualTo("FCT-181");
    }

    @Test
    void should_useCompositeIncomeFactCode_when_freelancer() {
        when(facts.require("FCT-182")).thenReturn(fact("FCT-182", "63000000"));
        RuleDocument document = employmentIncomeDocument();
        // 연 6600만 > 종합소득 상한 6300만 → FAIL. 급여소득자 상한(7500만)과 헷갈렸다면
        // FCT-181 을 찾다가 스텁이 없어 NEED_INFO 로 잘못 떨어졌을 것이다.
        PlanInput input = employmentInput(EmploymentType.FREELANCER, 5_500_000L);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isFalse();
        assertThat(results.get(0).factCode()).isEqualTo("FCT-182");
    }

    @Test
    void should_returnNeedInfo_when_unemployed() {
        RuleDocument document = employmentIncomeDocument();
        PlanInput input = employmentInput(EmploymentType.UNEMPLOYED, 0L);

        List<ConditionResult> results = engine.evaluate(document, input);

        assertThat(results.get(0).isMet()).isNull();
    }

    private RuleDocument employmentIncomeDocument() {
        return new RuleDocument(
                "AND",
                List.of(new RuleCondition(
                        "INCOME_CAP_BY_EMPLOYMENT",
                        "monthly_income",
                        "annual_lte_by_employment_type",
                        null,
                        "FCT-181",
                        null,
                        "FCT-182")));
    }

    private PlanInput employmentInput(EmploymentType employmentType, Long monthlyIncome) {
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
                        employmentType,
                        null,
                        null,
                        null,
                        null,
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

    @ParameterizedTest
    @CsvSource({
        "0,0",
        "10000000,50000000",
        "29999999,149999995",
        "30000000,150000000",
        "30000001,150000005",
        "50000000,200000000"
    })
    void should_boundRecommendedDepositByOwnCashAndRatio(long cash, long expected) {
        when(facts.require("FCT-008")).thenReturn(fact("FCT-008", "80"));
        when(facts.require("FCT-171")).thenReturn(fact("FCT-171", "150000000"));
        ExpectedEstimate estimate = engine.estimate(
                estimateDocument(), List.of(), depositInput(180_000_000L, cash), PolicyVerdictResult.PASS);
        assertThat(estimate.recommendedDepositLimit()).isEqualTo(expected);
        assertThat(estimate.estimatedLoanAmount()).isEqualTo(144_000_000L);
        assertThat(estimate.ownFundsRequired()).isEqualTo(36_000_000L);
    }

    @ParameterizedTest
    @CsvSource({"0,10000000", "100,160000000", "66.7,30030030"})
    void should_handleRatioBoundariesAndFractionalPercent(String ratio, long expected) {
        when(facts.require("FCT-008")).thenReturn(fact("FCT-008", ratio));
        when(facts.require("FCT-171")).thenReturn(fact("FCT-171", "150000000"));
        ExpectedEstimate estimate = engine.estimate(
                estimateDocument(), List.of(), depositInput(180_000_000L, 10_000_000L), PolicyVerdictResult.PASS);
        assertThat(estimate.recommendedDepositLimit()).isEqualTo(expected);
    }

    @Test
    void should_preserveAmount_when_rateSpecIsMissing() {
        when(facts.require("FCT-008")).thenReturn(fact("FCT-008", "80"));
        when(facts.require("FCT-171")).thenReturn(fact("FCT-171", "150000000"));
        var document = new RuleDocument("AND", List.of(), new AmountSpec("FCT-008", "FCT-171"), null);
        var estimate =
                engine.estimate(document, List.of(), depositInput(180_000_000L, null), PolicyVerdictResult.NEED_INFO);
        assertThat(estimate.isEmpty()).isFalse();
        assertThat(estimate.estimatedLoanAmount()).isEqualTo(144_000_000L);
        assertThat(estimate.recommendedDepositLimit()).isNull();
        assertThat(estimate.rateMin()).isNull();
        assertThat(estimate.rateMax()).isNull();
        assertThat(estimate.monthlyInterestMin()).isNull();
        assertThat(estimate.monthlyInterestMax()).isNull();
    }

    @ParameterizedTest
    @CsvSource(
            value = {"2.2,NULL", "3.3,2.2", "-1,2.2", "NULL,3.3"},
            nullValues = "NULL")
    void should_notInventRate_when_rangeIsIncompleteOrInvalid(String min, String max) {
        when(facts.require("FCT-008")).thenReturn(fact("FCT-008", "80"));
        when(facts.require("FCT-171")).thenReturn(fact("FCT-171", "150000000"));
        if (min != null) when(facts.require("FCT-172")).thenReturn(fact("FCT-172", min));
        if (max != null) when(facts.require("FCT-173")).thenReturn(fact("FCT-173", max));
        var estimate = engine.estimate(
                estimateDocument(), List.of(), depositInput(180_000_000L, null), PolicyVerdictResult.PASS);
        assertThat(estimate.estimatedLoanAmount()).isEqualTo(144_000_000L);
        assertThat(estimate.rateMin()).isNull();
        assertThat(estimate.rateMax()).isNull();
        assertThat(estimate.monthlyInterestMin()).isNull();
        assertThat(estimate.monthlyInterestMax()).isNull();
    }

    @Test
    void should_boundByDepositCap_butLeaveRecommendationUnknownWhenCapMissing() {
        when(facts.require("FCT-008")).thenReturn(fact("FCT-008", "80"));
        when(facts.require("FCT-171")).thenReturn(fact("FCT-171", "150000000"));
        var conditions = List.of(new ConditionResult("DEPOSIT_CAP", "상한", null, null, "CAP", null));
        var input = depositInput(180_000_000L, 100_000_000L);
        var unknown = engine.estimate(estimateDocument(), conditions, input, PolicyVerdictResult.NEED_INFO);
        assertThat(unknown.recommendedDepositLimit()).isNull();
        assertThat(unknown.estimatedLoanAmount()).isEqualTo(144_000_000L);
        when(facts.require("CAP")).thenReturn(fact("CAP", "200000000"));
        assertThat(engine.estimate(estimateDocument(), conditions, input, PolicyVerdictResult.PASS)
                        .recommendedDepositLimit())
                .isEqualTo(200_000_000L);
    }

    @Test
    void should_notOverflow_when_availableCashIsLongMax() {
        when(facts.require("FCT-008")).thenReturn(fact("FCT-008", "80"));
        when(facts.require("FCT-171")).thenReturn(fact("FCT-171", "150000000"));
        assertThat(engine.estimate(
                                estimateDocument(),
                                List.of(),
                                depositInput(180_000_000L, Long.MAX_VALUE),
                                PolicyVerdictResult.PASS)
                        .recommendedDepositLimit())
                .isEqualTo(Long.MAX_VALUE);
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
