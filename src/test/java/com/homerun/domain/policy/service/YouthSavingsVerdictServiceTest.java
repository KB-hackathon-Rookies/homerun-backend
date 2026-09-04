package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.policy.dto.response.PolicyVerdictResponse;
import com.homerun.domain.policy.entity.Policy;
import com.homerun.domain.policy.entity.PolicyRule;
import com.homerun.domain.policy.entity.PolicyVerdict;
import com.homerun.domain.policy.model.ConditionResult;
import com.homerun.domain.policy.model.RuleDocument;
import com.homerun.domain.policy.repository.PolicyRepository;
import com.homerun.domain.policy.repository.PolicyRuleRepository;
import com.homerun.domain.policy.repository.PolicyVerdictRepository;
import com.homerun.domain.policy.repository.VerdictBasisRepository;
import com.homerun.domain.policy.type.PolicyRuleStatus;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 전세/월세 구분 없이 동작하는지, WOLSE 계획도 막지 않는지가
 * {@link JeonsePolicyVerdictServiceTest}와 다른 핵심 검증 포인트다.
 */
class YouthSavingsVerdictServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;
    private static final Long POLICY_ID = 1L;
    private static final Long RULE_ID = 100L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC);

    private final PlanRepository plans = mock(PlanRepository.class);
    private final PlanInputRepository inputs = mock(PlanInputRepository.class);
    private final PolicyRepository policies = mock(PolicyRepository.class);
    private final PolicyRuleRepository rules = mock(PolicyRuleRepository.class);
    private final PolicyVerdictRepository verdicts = mock(PolicyVerdictRepository.class);
    private final VerdictBasisRepository basisRepository = mock(VerdictBasisRepository.class);
    private final PolicyRuleEngine engine = mock(PolicyRuleEngine.class);
    private final YouthSavingsVerdictService service =
            new YouthSavingsVerdictService(plans, inputs, policies, rules, verdicts, basisRepository, engine, CLOCK);

    @BeforeEach
    void setUp() {
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(PlanInput.create(PLAN_ID, emptyRequest())));
        when(verdicts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(POLICY_ID);
        when(policy.getCode()).thenReturn("YOUTH-FUTURE-SAVINGS");
        when(policy.getName()).thenReturn("청년미래적금");
        when(policies.findByCode("YOUTH-FUTURE-SAVINGS")).thenReturn(Optional.of(policy));

        PolicyRule rule = mock(PolicyRule.class);
        when(rule.getId()).thenReturn(RULE_ID);
        when(rule.getRuleJson()).thenReturn(new RuleDocument("AND", List.of()));
        when(rule.getVersion()).thenReturn(1);
        when(rules.findFirstByPolicyIdAndStatusOrderByVersionDesc(POLICY_ID, PolicyRuleStatus.ACTIVE))
                .thenReturn(Optional.of(rule));
    }

    @Test
    void should_evaluate_when_planIsWolse() {
        // JeonsePolicyVerdictService 와 다르게 lease_type 제한이 없어야 한다 — 적금은 전세·월세
        // 구분이 없는 상품이다.
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.WOLSE, LocalDate.of(2026, 12, 1))));
        List<ConditionResult> pass = List.of(new ConditionResult("AGE_RANGE", "연령", "만19~34세", true, null, null));
        when(engine.evaluate(any(), any())).thenReturn(pass);

        PolicyVerdictResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        assertThat(response.verdict()).isEqualTo(PolicyVerdictResult.PASS);
        assertThat(response.policyCode()).isEqualTo("YOUTH-FUTURE-SAVINGS");
    }

    @Test
    void should_aggregateAsNeedInfo_when_householdIncomeConditionUnknown() {
        // 이 정책의 핵심 특성: 가구소득 조건이 항상 NEED_INFO라 연령·소득이 통과해도 전체는
        // NEED_INFO여야 한다.
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, LocalDate.of(2026, 12, 1))));
        List<ConditionResult> results = List.of(
                new ConditionResult("AGE_RANGE", "연령", "만19~34세", true, null, null),
                new ConditionResult("INCOME_CAP_BY_EMPLOYMENT", "개인소득", "연 7500만 이하", true, null, null),
                new ConditionResult("HOUSEHOLD_INCOME_RATIO", "가구소득", null, null, null, null));
        when(engine.evaluate(any(), any())).thenReturn(results);

        PolicyVerdictResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        assertThat(response.verdict()).isEqualTo(PolicyVerdictResult.NEED_INFO);
        // API 공통계약: missingFields[] 는 NEED_INFO 조건만 담는다. PASS 조건은 안 들어간다.
        assertThat(response.missingFields()).containsExactly("HOUSEHOLD_INCOME_RATIO");
        assertThat(response.ruleVersion()).isEqualTo(1);
    }

    @Test
    void should_returnEmptyRejectionReasonsAndNullEstimate_always() {
        // 대안 정책·예상 혜택 개념이 없다 — 항상 비어 있어야 한다.
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, LocalDate.of(2026, 12, 1))));
        List<ConditionResult> fail = List.of(new ConditionResult("AGE_RANGE", "연령", "만19~34세", false, null, null));
        when(engine.evaluate(any(), any())).thenReturn(fail);

        PolicyVerdictResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        assertThat(response.rejectionReasons()).isEmpty();
        assertThat(response.estimate()).isNull();
    }

    @Test
    void should_returnNeedInfo_when_noActiveRuleExists() {
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, LocalDate.of(2026, 12, 1))));
        when(rules.findFirstByPolicyIdAndStatusOrderByVersionDesc(POLICY_ID, PolicyRuleStatus.ACTIVE))
                .thenReturn(Optional.empty());

        PolicyVerdictResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        assertThat(response.verdict()).isEqualTo(PolicyVerdictResult.NEED_INFO);
        assertThat(response.basis()).extracting(basis -> basis.code()).containsExactly("RULE_NOT_ACTIVE");
        assertThat(response.ruleVersion()).isNull();
    }

    @Test
    void should_throw_when_requesterIsNotPlanOwner() {
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(999L, LeaseType.JEONSE, LocalDate.of(2026, 12, 1))));

        assertThatThrownBy(() -> service.evaluate(MEMBER_ID, PLAN_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        e -> assertThat(((BusinessException) e).errorCode()).isEqualTo(ErrorCode.PLAN_ACCESS_DENIED));
    }

    @Test
    void should_overwriteExistingVerdict_when_reevaluating() {
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, LocalDate.of(2026, 12, 1))));
        List<ConditionResult> pass = List.of(new ConditionResult("AGE_RANGE", "연령", "만19~34세", true, null, null));
        when(engine.evaluate(any(), any())).thenReturn(pass);
        PolicyVerdict existing = PolicyVerdict.create(
                PLAN_ID, POLICY_ID, RULE_ID, null, PolicyVerdictResult.NEED_INFO, null, null, "old-version");
        when(verdicts.findByPlanIdAndPolicyIdAndRuleId(PLAN_ID, POLICY_ID, RULE_ID))
                .thenReturn(Optional.of(existing));

        PolicyVerdictResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        assertThat(response.verdict()).isEqualTo(PolicyVerdictResult.PASS);
    }

    private PlanInputRequest emptyRequest() {
        return new PlanInputRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, true, Set.of());
    }
}
