package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.policy.dto.response.ConditionBasisResponse;
import com.homerun.domain.policy.dto.response.JeonsePolicyVerdictListResponse;
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
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.repository.PropertyRepository;
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
import org.mockito.ArgumentCaptor;

/**
 * 룰엔진 결과를 어떻게 종합·저장하는지만 본다 — 조건 하나하나의 해석은
 * {@link PolicyRuleEngineTest} 담당이라 여기서는 {@link PolicyRuleEngine} 을 mock 한다.
 */
class JeonsePolicyVerdictServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC);
    private static final Long PROPERTY_ID = 100L;
    private static final List<String> CODES =
            List.of("JEONSE-YOUTH-BEOTIMMOK", "JEONSE-GENERAL-BEOTIMMOK", "JEONSE-SEOUL-INTEREST-SUPPORT");
    private static final List<String> RETURN_GUARANTEE_CODES =
            List.of("RETURN-GUARANTEE-HUG", "RETURN-GUARANTEE-HF", "RETURN-GUARANTEE-SGI");

    private final PlanRepository plans = mock(PlanRepository.class);
    private final PlanInputRepository inputs = mock(PlanInputRepository.class);
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final PolicyRepository policies = mock(PolicyRepository.class);
    private final PolicyRuleRepository rules = mock(PolicyRuleRepository.class);
    private final PolicyVerdictRepository verdicts = mock(PolicyVerdictRepository.class);
    private final VerdictBasisRepository basisRepository = mock(VerdictBasisRepository.class);
    private final PolicyRuleEngine engine = mock(PolicyRuleEngine.class);
    private final JeonsePolicyVerdictService service = new JeonsePolicyVerdictService(
            plans, inputs, properties, policies, rules, verdicts, basisRepository, engine, CLOCK);

    @BeforeEach
    void setUp() {
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, LocalDate.of(2026, 12, 1))));
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(PlanInput.create(PLAN_ID, emptyRequest())));
        when(verdicts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        long id = 1L;
        for (String code : CODES) {
            id = stubPolicyAndRule(code, id);
        }
        for (String code : RETURN_GUARANTEE_CODES) {
            id = stubPolicyAndRule(code, id);
        }
    }

    /**
     * policyStub()/ruleStub() 자체가 완결된 when/thenReturn 쌍을 만든다. 그 호출을 바깥
     * when(...).thenReturn(...) 의 인자 자리에서 바로 하면, 인자를 계산하는 동안 안쪽 when()
     * 이 끼어들어 Mockito 가 UnfinishedStubbingException 을 던진다. 그래서 변수에 먼저 담아
     * 완결시킨 뒤에 넘긴다.
     */
    private long stubPolicyAndRule(String code, long id) {
        Policy policy = policyStub(id, code);
        PolicyRule rule = ruleStub(id + 100);
        when(policies.findByCode(code)).thenReturn(Optional.of(policy));
        when(rules.findFirstByPolicyIdAndStatusOrderByVersionDesc(id, PolicyRuleStatus.ACTIVE))
                .thenReturn(Optional.of(rule));
        return id + 1;
    }

    @Test
    void should_aggregateAsFail_when_anyConditionIsNotMet() {
        List<ConditionResult> pass = List.of(new ConditionResult("A", "라벨", "텍스트", true, null, null));
        List<ConditionResult> fail = List.of(new ConditionResult("B", "라벨", "텍스트", false, null, null));
        when(engine.evaluate(any(), any(), any())).thenReturn(pass, pass, fail);

        JeonsePolicyVerdictListResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        assertThat(response.results())
                .extracting(PolicyVerdictResponse::verdict)
                .containsExactly(PolicyVerdictResult.PASS, PolicyVerdictResult.PASS, PolicyVerdictResult.FAIL);
    }

    @Test
    void should_aggregateAsNeedInfo_when_someConditionIsUnknownAndNoneFailed() {
        List<ConditionResult> pass = List.of(new ConditionResult("A", "라벨", "텍스트", true, null, null));
        List<ConditionResult> needInfo = List.of(new ConditionResult("C", "라벨", "텍스트", null, null, null));
        when(engine.evaluate(any(), any(), any())).thenReturn(pass, needInfo, pass);

        JeonsePolicyVerdictListResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        assertThat(response.results().get(1).verdict()).isEqualTo(PolicyVerdictResult.NEED_INFO);
    }

    @Test
    void should_returnNeedInfo_when_noActiveRuleExists() {
        // 3번째 정책(SEOUL, policy id=3)만 검수된 조건식이 없다고 덮어쓴다.
        when(rules.findFirstByPolicyIdAndStatusOrderByVersionDesc(3L, PolicyRuleStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(engine.evaluate(any(), any(), any()))
                .thenReturn(List.of(new ConditionResult("A", "라벨", "텍스트", true, null, null)));

        JeonsePolicyVerdictListResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        PolicyVerdictResponse seoul = response.results().get(2);
        assertThat(seoul.verdict()).isEqualTo(PolicyVerdictResult.NEED_INFO);
        assertThat(seoul.basis()).extracting(ConditionBasisResponse::code).containsExactly("RULE_NOT_ACTIVE");
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
    void should_throw_when_planIsWolse() {
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.WOLSE, LocalDate.of(2026, 12, 1))));

        assertThatThrownBy(() -> service.evaluate(MEMBER_ID, PLAN_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode())
                        .isEqualTo(ErrorCode.POLICY_JEONSE_PLAN_REQUIRED));
    }

    @Test
    void should_evaluateReturnGuarantees_and_stampPropertyIdOnSavedVerdict() {
        Property property = mock(Property.class);
        when(property.getId()).thenReturn(PROPERTY_ID);
        when(properties.findByIdAndPlanId(PROPERTY_ID, PLAN_ID)).thenReturn(Optional.of(property));
        when(engine.evaluate(any(), any(), any()))
                .thenReturn(List.of(new ConditionResult("PRICE_RATIO_126", "라벨", "텍스트", true, "FCT-054", null)));

        JeonsePolicyVerdictListResponse response = service.evaluateReturnGuarantees(MEMBER_ID, PLAN_ID, PROPERTY_ID);

        assertThat(response.results()).hasSize(3);
        assertThat(response.results())
                .extracting(PolicyVerdictResponse::verdict)
                .containsOnly(PolicyVerdictResult.PASS);

        ArgumentCaptor<PolicyVerdict> captor = ArgumentCaptor.forClass(PolicyVerdict.class);
        verify(verdicts, times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PolicyVerdict::getPropertyId)
                .containsOnly(PROPERTY_ID);
    }

    @Test
    void should_throw_when_propertyNotInPlan() {
        when(properties.findByIdAndPlanId(PROPERTY_ID, PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.evaluateReturnGuarantees(MEMBER_ID, PLAN_ID, PROPERTY_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        e -> assertThat(((BusinessException) e).errorCode()).isEqualTo(ErrorCode.PROPERTY_NOT_IN_PLAN));
    }

    private Policy policyStub(long id, String code) {
        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(id);
        when(policy.getCode()).thenReturn(code);
        when(policy.getName()).thenReturn(code + "-이름");
        return policy;
    }

    private PolicyRule ruleStub(long id) {
        PolicyRule rule = mock(PolicyRule.class);
        when(rule.getId()).thenReturn(id);
        when(rule.getRuleJson()).thenReturn(new RuleDocument("AND", List.of()));
        return rule;
    }

    private PlanInputRequest emptyRequest() {
        return new PlanInputRequest(
                null, // hopeDeposit
                null, // currentDeposit
                null, // monthlyRent
                null, // maintenanceFee
                null, // maxMonthlyBurden
                null, // regionId
                null, // areaM2
                null, // houseType
                null, // isHomeless
                null, // householderStatus
                null, // maritalStatus
                null, // employmentType
                null, // employmentMonths
                null, // companySize
                null, // householdHomeless
                null, // birthDate
                null, // militaryMonths
                null, // monthlyIncome
                null, // netAssets
                null, // availableCash
                null, // existingJeonseLoan
                null, // incomeSource
                null, // assetSource
                true, // financialDataConfirmed
                Set.of()); // unknownFields
    }
}
