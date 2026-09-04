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
import com.homerun.domain.policy.dto.response.RejectionReasonResponse;
import com.homerun.domain.policy.entity.Policy;
import com.homerun.domain.policy.entity.PolicyRule;
import com.homerun.domain.policy.entity.PolicyVerdict;
import com.homerun.domain.policy.model.ConditionResult;
import com.homerun.domain.policy.model.ExpectedEstimate;
import com.homerun.domain.policy.model.RuleDocument;
import com.homerun.domain.policy.repository.PolicyRepository;
import com.homerun.domain.policy.repository.PolicyRuleRepository;
import com.homerun.domain.policy.repository.PolicyVerdictRepository;
import com.homerun.domain.policy.repository.RejectionReasonRepository;
import com.homerun.domain.policy.repository.VerdictBasisRepository;
import com.homerun.domain.policy.type.PolicyRuleStatus;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import com.homerun.domain.policy.type.RejectionReasonCategory;
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
    private final RejectionReasonRepository rejectionReasons = mock(RejectionReasonRepository.class);
    private final PolicyRuleEngine engine = mock(PolicyRuleEngine.class);
    private final JeonsePolicyVerdictService service = new JeonsePolicyVerdictService(
            plans, inputs, properties, policies, rules, verdicts, basisRepository, rejectionReasons, engine, CLOCK);

    @BeforeEach
    void setUp() {
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, LocalDate.of(2026, 12, 1))));
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(PlanInput.create(PLAN_ID, emptyRequest())));
        when(verdicts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(engine.estimate(any(), any(), any(), any())).thenReturn(ExpectedEstimate.empty());

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
    void should_includeGeneralCardWithShortfall_when_availableCashIsInsufficient() {
        PlanInput input = mock(PlanInput.class);
        when(input.getAvailableCash()).thenReturn(10_000_000L);
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));
        when(engine.evaluate(any(), any(), any()))
                .thenReturn(List.of(new ConditionResult("A", "조건", null, true, null, null)));
        when(engine.estimate(any(), any(), any(), any()))
                .thenReturn(new ExpectedEstimate(null, 144_000_000L, 36_000_000L, null, null, null, null));

        var response = service.evaluate(MEMBER_ID, PLAN_ID);

        assertThat(response.cards()).hasSize(4);
        assertThat(response.cards().get(1).code()).isEqualTo("JEONSE-GENERAL-BEOTIMMOK");
        assertThat(response.cards().get(1).ownFundsShortfall()).isEqualTo(26_000_000L);
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
    void should_computeDaysRemaining_when_conditionHasEligibleUntil() {
        // POL-01-04. CLOCK 이 2026-09-04 고정이라, eligibleUntil 2026-09-14 이면 10일 남아야 한다.
        List<ConditionResult> pass = List.of(new ConditionResult("A", "라벨", "텍스트", true, null, null));
        List<ConditionResult> withDeadline = List.of(new ConditionResult(
                "AGE_UPPER_BOUND", "연령 상한", "만 34세 이하", true, "FCT-174", null, LocalDate.of(2026, 9, 14)));
        when(engine.evaluate(any(), any(), any())).thenReturn(withDeadline, pass, pass);

        JeonsePolicyVerdictListResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        ConditionBasisResponse basis = response.results().get(0).basis().get(0);
        assertThat(basis.eligibleUntil()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(basis.daysRemaining()).isEqualTo(10L);
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
        // API 공통계약: 판정에 쓸 규칙이 없으면 ruleVersion 도 없다.
        assertThat(seoul.ruleVersion()).isNull();
    }

    @Test
    void should_includeRuleVersion_when_activeRuleExists() {
        // API 공통계약(Notion API 명세서 "판정 응답의 공통 계약"): ruleVersion — 판정에 쓴 규칙 버전.
        List<ConditionResult> pass = List.of(new ConditionResult("A", "라벨", "텍스트", true, null, null));
        when(engine.evaluate(any(), any(), any())).thenReturn(pass, pass, pass);

        JeonsePolicyVerdictListResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        assertThat(response.results())
                .extracting(PolicyVerdictResponse::ruleVersion)
                .containsOnly(1);
    }

    @Test
    void should_includeMissingFields_when_conditionIsNeedInfo() {
        // API 공통계약: missingFields[] — NEED_INFO 판정의 원인. PASS/FAIL 조건은 안 들어간다.
        List<ConditionResult> mixed = List.of(
                new ConditionResult("HOUSEHOLD_HOMELESS", "무주택", "텍스트", true, null, null),
                new ConditionResult("INCOME_CAP", "소득", "텍스트", null, null, null),
                new ConditionResult("NET_ASSET_CAP", "자산", "텍스트", null, null, null));
        when(engine.evaluate(any(), any(), any())).thenReturn(mixed, mixed, mixed);

        JeonsePolicyVerdictListResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        assertThat(response.results().get(0).missingFields()).containsExactly("INCOME_CAP", "NET_ASSET_CAP");
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
    void should_includeAlternativePolicy_when_failedConditionHasMapping() {
        List<ConditionResult> pass = List.of(new ConditionResult("A", "라벨", "텍스트", true, null, null));
        List<ConditionResult> failWithAlternative =
                List.of(new ConditionResult("AGE_UPPER_BOUND", "연령 상한", "만 34세 이하", false, "FCT-174", null));
        when(engine.evaluate(any(), any(), any())).thenReturn(failWithAlternative, pass, pass);

        JeonsePolicyVerdictListResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        PolicyVerdictResponse youth = response.results().get(0);
        assertThat(youth.verdict()).isEqualTo(PolicyVerdictResult.FAIL);
        assertThat(youth.rejectionReasons()).hasSize(1);
        RejectionReasonResponse reason = youth.rejectionReasons().get(0);
        assertThat(reason.reasonCode()).isEqualTo("AGE_UPPER_BOUND");
        assertThat(reason.alternativePolicyCode()).isEqualTo("JEONSE-GENERAL-BEOTIMMOK");
    }

    @Test
    void should_haveNoAlternative_when_failedConditionIsUnmapped() {
        List<ConditionResult> pass = List.of(new ConditionResult("A", "라벨", "텍스트", true, null, null));
        List<ConditionResult> failUnmapped =
                List.of(new ConditionResult("HOUSEHOLD_HOMELESS", "무주택", "무주택", false, null, null));
        when(engine.evaluate(any(), any(), any())).thenReturn(failUnmapped, pass, pass);

        JeonsePolicyVerdictListResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        RejectionReasonResponse reason =
                response.results().get(0).rejectionReasons().get(0);
        assertThat(reason.alternativePolicyCode()).isNull();
    }

    @Test
    void should_returnEmptyRejectionReasons_when_verdictIsPass() {
        List<ConditionResult> pass = List.of(new ConditionResult("A", "라벨", "텍스트", true, null, null));
        when(engine.evaluate(any(), any(), any())).thenReturn(pass, pass, pass);

        JeonsePolicyVerdictListResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        assertThat(response.results().get(0).rejectionReasons()).isEmpty();
    }

    @Test
    void should_notRecommendSelf_when_alternativeCodeEqualsFailingPolicy() {
        // AGE_UPPER_BOUND의 매핑 대안은 JEONSE-GENERAL-BEOTIMMOK 이다. 두번째 정책(GENERAL,
        // id=2) 자체가 이 조건으로 실패해도 자기 자신을 대안으로 추천하면 안 된다.
        List<ConditionResult> pass = List.of(new ConditionResult("A", "라벨", "텍스트", true, null, null));
        List<ConditionResult> failSelfReferential =
                List.of(new ConditionResult("AGE_UPPER_BOUND", "연령 상한", "텍스트", false, "FCT-174", null));
        when(engine.evaluate(any(), any(), any())).thenReturn(pass, failSelfReferential, pass);

        JeonsePolicyVerdictListResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        RejectionReasonResponse reason =
                response.results().get(1).rejectionReasons().get(0);
        assertThat(reason.alternativePolicyCode()).isNull();
    }

    @Test
    void should_categorizeAsUser_when_failedConditionIsIncomeCap() {
        // POL-03-10: 소득·자산·연령·세대주 등 사람에 관한 조건은 USER.
        List<ConditionResult> pass = List.of(new ConditionResult("A", "라벨", "텍스트", true, null, null));
        List<ConditionResult> failIncomeCap =
                List.of(new ConditionResult("INCOME_CAP", "소득 기준", "연 5000만 이하", false, "FCT-003", null));
        when(engine.evaluate(any(), any(), any())).thenReturn(failIncomeCap, pass, pass);

        JeonsePolicyVerdictListResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        RejectionReasonResponse reason =
                response.results().get(0).rejectionReasons().get(0);
        assertThat(reason.category()).isEqualTo(RejectionReasonCategory.USER);
    }

    @Test
    void should_categorizeAsHouse_when_failedConditionIsViolationBuilding() {
        // 매물 자체의 안전성·유형에 관한 조건은 HOUSE.
        List<ConditionResult> pass = List.of(new ConditionResult("A", "라벨", "텍스트", true, null, null));
        List<ConditionResult> failHouseCondition =
                List.of(new ConditionResult("NOT_VIOLATION_BUILDING", "위반건축물 아님", "위반건축물 아님", false, null, null));
        when(engine.evaluate(any(), any(), any())).thenReturn(failHouseCondition, pass, pass);

        JeonsePolicyVerdictListResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        RejectionReasonResponse reason =
                response.results().get(0).rejectionReasons().get(0);
        assertThat(reason.category()).isEqualTo(RejectionReasonCategory.HOUSE);
    }

    @Test
    void should_categorizeAsLimit_when_failedConditionIsDepositCap() {
        // 상품이 처리 가능한 금액 자체의 상한은 LIMIT — 사람도 집도 아니라 대안이 다르다.
        List<ConditionResult> pass = List.of(new ConditionResult("A", "라벨", "텍스트", true, null, null));
        List<ConditionResult> failDepositCap =
                List.of(new ConditionResult("DEPOSIT_CAP", "임차보증금 상한", "3억원 이하", false, "FCT-175", null));
        when(engine.evaluate(any(), any(), any())).thenReturn(failDepositCap, pass, pass);

        JeonsePolicyVerdictListResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        RejectionReasonResponse reason =
                response.results().get(0).rejectionReasons().get(0);
        assertThat(reason.category()).isEqualTo(RejectionReasonCategory.LIMIT);
    }

    @Test
    void should_haveNoCategory_when_conditionCodeIsUnknown() {
        // 지어낸 분류를 붙이지 않는다 — 매핑에 없는 코드는 category 가 null 이어야 한다.
        List<ConditionResult> pass = List.of(new ConditionResult("A", "라벨", "텍스트", true, null, null));
        List<ConditionResult> failUnknown =
                List.of(new ConditionResult("SOME_FUTURE_CONDITION", "라벨", "텍스트", false, null, null));
        when(engine.evaluate(any(), any(), any())).thenReturn(failUnknown, pass, pass);

        JeonsePolicyVerdictListResponse response = service.evaluate(MEMBER_ID, PLAN_ID);

        RejectionReasonResponse reason =
                response.results().get(0).rejectionReasons().get(0);
        assertThat(reason.category()).isNull();
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
        assertThat(response.cards()).isEmpty();
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
    void should_stampPropertyId_when_evaluatingFundLoanWithProperty() {
        // #100: propertyId 를 주면 청년/일반버팀목·서울시이자지원도 집 조건을 같이 본다.
        Property property = mock(Property.class);
        when(property.getId()).thenReturn(PROPERTY_ID);
        when(properties.findByIdAndPlanId(PROPERTY_ID, PLAN_ID)).thenReturn(Optional.of(property));
        when(engine.evaluate(any(), any(), any()))
                .thenReturn(List.of(new ConditionResult("A", "라벨", "텍스트", true, null, null)));

        service.evaluate(MEMBER_ID, PLAN_ID, PROPERTY_ID);

        ArgumentCaptor<Property> propertyCaptor = ArgumentCaptor.forClass(Property.class);
        verify(engine, times(3)).evaluate(any(), any(), propertyCaptor.capture());
        assertThat(propertyCaptor.getAllValues()).allMatch(p -> p == property);
    }

    @Test
    void should_passNullProperty_when_evaluatingFundLoanWithoutPropertyId() {
        when(engine.evaluate(any(), any(), any()))
                .thenReturn(List.of(new ConditionResult("A", "라벨", "텍스트", true, null, null)));

        service.evaluate(MEMBER_ID, PLAN_ID);

        ArgumentCaptor<Property> propertyCaptor = ArgumentCaptor.forClass(Property.class);
        verify(engine, times(3)).evaluate(any(), any(), propertyCaptor.capture());
        assertThat(propertyCaptor.getAllValues()).allMatch(p -> p == null);
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
        when(rule.getVersion()).thenReturn(1);
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
