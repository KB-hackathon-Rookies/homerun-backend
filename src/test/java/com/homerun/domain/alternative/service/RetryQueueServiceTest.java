package com.homerun.domain.alternative.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.alternative.dto.response.RetryQueueResponse;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.policy.entity.Policy;
import com.homerun.domain.policy.entity.PolicyRule;
import com.homerun.domain.policy.model.RuleCondition;
import com.homerun.domain.policy.model.RuleDocument;
import com.homerun.domain.policy.repository.PolicyRepository;
import com.homerun.domain.policy.repository.PolicyRuleRepository;
import com.homerun.domain.policy.service.PolicyRuleEngine;
import com.homerun.domain.policy.type.PolicyRuleStatus;
import com.homerun.domain.region.service.PolicyRegionResolver;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 판정 로직(age_range_adjusted) 자체는 PolicyRuleEngineTest가 보므로, 여기서는 진짜 엔진을
 * 그대로 붙여 정책 훑기·정렬·필터링만 검증한다. */
class RetryQueueServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;
    private static final Long POLICY_ID = 100L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC);

    private final PlanRepository plans = mock(PlanRepository.class);
    private final PlanInputRepository inputs = mock(PlanInputRepository.class);
    private final PolicyRepository policies = mock(PolicyRepository.class);
    private final PolicyRuleRepository rules = mock(PolicyRuleRepository.class);
    private final FactRegistry facts = mock(FactRegistry.class);
    private final PolicyRuleEngine engine = new PolicyRuleEngine(facts, CLOCK, mock(PolicyRegionResolver.class));
    private final RetryQueueService service = new RetryQueueService(plans, inputs, policies, rules, engine, CLOCK);

    @BeforeEach
    void setUp() {
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, LocalDate.of(2026, 12, 1))));
    }

    @Test
    void should_returnPolicy_when_stillTooYoungForActiveAgeRangeRule() {
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input(LocalDate.of(2008, 1, 1))));
        PolicyRule rule = activeRule();
        when(rules.findAllByStatus(PolicyRuleStatus.ACTIVE)).thenReturn(List.of(rule));
        Policy policy = mockPolicy();
        when(policies.findAllById(any())).thenReturn(List.of(policy));
        when(facts.require("FCT-179"))
                .thenReturn(new Fact("FCT-179", "청년미래적금 연령 하한", new BigDecimal("19"), "세", "만 19세 이상", "url", false));

        RetryQueueResponse response = service.build(MEMBER_ID, PLAN_ID);

        assertThat(response.items()).hasSize(1);
        var item = response.items().get(0);
        assertThat(item.policyCode()).isEqualTo("YOUTH-FUTURE-SAVINGS");
        assertThat(item.eligibleFrom()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(item.daysRemaining()).isEqualTo(119);
    }

    @Test
    void should_returnEmpty_when_ageAlreadyMet() {
        // 2000-01-01 생일이면 이미 19세를 한참 넘겨 재도전 큐에 낄 이유가 없다.
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input(LocalDate.of(2000, 1, 1))));
        PolicyRule rule = activeRule();
        when(rules.findAllByStatus(PolicyRuleStatus.ACTIVE)).thenReturn(List.of(rule));
        Policy policy = mockPolicy();
        when(policies.findAllById(any())).thenReturn(List.of(policy));
        when(facts.require("FCT-179"))
                .thenReturn(new Fact("FCT-179", "청년미래적금 연령 하한", new BigDecimal("19"), "세", "만 19세 이상", "url", false));

        RetryQueueResponse response = service.build(MEMBER_ID, PLAN_ID);

        assertThat(response.items()).isEmpty();
    }

    @Test
    void should_returnEmpty_when_birthDateUnknown() {
        // 생년월일을 모르면 채우는 날짜 자체를 계산할 수 없다 — 추측해서 채우지 않는다.
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input(null)));

        RetryQueueResponse response = service.build(MEMBER_ID, PLAN_ID);

        assertThat(response.items()).isEmpty();
    }

    @Test
    void should_pickLatestVersion_when_multipleActiveVersionsExistForSamePolicy() {
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input(LocalDate.of(2008, 1, 1))));
        PolicyRule older = activeRule();
        PolicyRule newer = mock(PolicyRule.class);
        when(newer.getPolicyId()).thenReturn(POLICY_ID);
        when(newer.getVersion()).thenReturn(2);
        when(newer.getRuleJson()).thenReturn(new RuleDocument("AND", List.of()));
        when(rules.findAllByStatus(PolicyRuleStatus.ACTIVE)).thenReturn(List.of(older, newer));
        Policy policy = mockPolicy();
        when(policies.findAllById(any())).thenReturn(List.of(policy));

        RetryQueueResponse response = service.build(MEMBER_ID, PLAN_ID);

        // items가 비는 건 older가 골라져도(FCT-179 미스텁 시 null) 우연히 같을 수 있다 —
        // FCT-179를 아예 조회 안 했는지까지 봐야 newer가 실제로 선택됐다고 말할 수 있다.
        verify(facts, never()).require(any());
        // 최신 버전(newer, 조건 없음)만 훑으니 FCT-179를 아예 조회하지 않고, 큐도 비어야 한다.
        assertThat(response.items()).isEmpty();
    }

    private PlanInput input(LocalDate birthDate) {
        return PlanInput.create(
                PLAN_ID,
                new PlanInputRequest(
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                        birthDate, null, null, null, null, null, null, null, true, Set.of()));
    }

    private PolicyRule activeRule() {
        PolicyRule rule = mock(PolicyRule.class);
        when(rule.getPolicyId()).thenReturn(POLICY_ID);
        when(rule.getVersion()).thenReturn(1);
        when(rule.getRuleJson())
                .thenReturn(new RuleDocument(
                        "AND",
                        List.of(new RuleCondition(
                                "AGE_RANGE", "birth_date", "age_range_adjusted", null, "FCT-179", null, "FCT-180"))));
        return rule;
    }

    private Policy mockPolicy() {
        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(POLICY_ID);
        when(policy.getCode()).thenReturn("YOUTH-FUTURE-SAVINGS");
        when(policy.getName()).thenReturn("청년미래적금");
        return policy;
    }
}
