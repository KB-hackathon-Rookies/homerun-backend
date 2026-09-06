package com.homerun.domain.property.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.policy.entity.Policy;
import com.homerun.domain.policy.entity.PolicyRule;
import com.homerun.domain.policy.model.ConditionResult;
import com.homerun.domain.policy.repository.PolicyRepository;
import com.homerun.domain.policy.repository.PolicyRuleRepository;
import com.homerun.domain.policy.service.PolicyRuleEngine;
import com.homerun.domain.policy.type.PolicyRuleStatus;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import com.homerun.domain.property.dto.response.PropertyPolicyVerdictListResponse;
import com.homerun.domain.property.dto.response.PropertyPolicyVerdictResponse;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.entity.PropertyPolicyVerdict;
import com.homerun.domain.property.repository.PropertyPolicyVerdictRepository;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매물 하나를 전세 상품마다 판정하고 매물별로 남긴다(DR-10 · BR-09).
 *
 * <p>판정 로직을 새로 만들지 않는다. {@link PolicyRuleEngine} 에 매물을 함께 넘겨 이미 검수된
 * 조건식을 그대로 돌린다 — 같은 판정을 두 곳에서 하면 반드시 갈라진다.
 *
 * <p>{@code policy_verdict} 가 아니라 별도 테이블에 쓰는 이유는 그쪽 UNIQUE 제약이
 * (plan, policy, rule) 이라 매물을 여러 개 판정하면 마지막 하나만 남기 때문이다. 명세서는 매물을
 * 최대 5개까지 등록하고 최대 3개를 비교하므로(FR-P1-02) 매물별 판정을 각각 보존해야 한다.
 */
@Service
public class PropertyPolicyVerdictService {

    /** 2루에서 매물마다 판정할 전세 상품. 은행(국민은행)은 policy 행이 없고 BR-05 가 "조건 판정
     * 없이 항상 매칭"이라 여기 없다. */
    private static final List<String> JEONSE_POLICY_CODES =
            List.of("JEONSE-YOUTH-BEOTIMMOK", "JEONSE-GENERAL-BEOTIMMOK", "JEONSE-SEOUL-INTEREST-SUPPORT");

    /**
     * 조건 코드 → 명세서 2-1 의 STEP 번호. 어디서 걸렸는지 한 줄로 보여주기 위한 것이다(FR-P1-04).
     *
     * <p>여기 없는 조건은 사람 조건(1루)이라 STEP 을 붙이지 않는다 — 2루 화면의 단계가 아니다.
     */
    private static final Map<String, Short> FAIL_STEP_BY_CONDITION = Map.of(
            "AREA_CAP", (short) 2,
            "DEPOSIT_CAP", (short) 2,
            "REGION_TARGET", (short) 2,
            "NOT_VIOLATION_BUILDING", (short) 3,
            "NOT_MULTI_HOUSEHOLD", (short) 3);

    private final PlanRepository plans;
    private final PlanInputRepository inputs;
    private final PropertyRepository properties;
    private final PolicyRepository policies;
    private final PolicyRuleRepository rules;
    private final PropertyPolicyVerdictRepository verdicts;
    private final PolicyRuleEngine engine;
    private final Clock clock;

    public PropertyPolicyVerdictService(
            PlanRepository plans,
            PlanInputRepository inputs,
            PropertyRepository properties,
            PolicyRepository policies,
            PolicyRuleRepository rules,
            PropertyPolicyVerdictRepository verdicts,
            PolicyRuleEngine engine,
            Clock clock) {
        this.plans = plans;
        this.inputs = inputs;
        this.properties = properties;
        this.policies = policies;
        this.rules = rules;
        this.verdicts = verdicts;
        this.engine = engine;
        this.clock = clock;
    }

    @Transactional
    public PropertyPolicyVerdictListResponse evaluate(Long memberId, Long planId, Long propertyId) {
        verifyOwnership(memberId, planId);
        Property property = properties
                .findByIdAndPlanId(propertyId, planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPERTY_NOT_IN_PLAN));
        PlanInput input =
                inputs.findByPlanId(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_INPUT_NOT_FOUND));

        Instant now = Instant.now(clock);
        List<PropertyPolicyVerdictResponse> results = JEONSE_POLICY_CODES.stream()
                .map(code -> evaluateOne(property, input, code, now))
                .toList();
        return new PropertyPolicyVerdictListResponse(propertyId, results, now);
    }

    @Transactional(readOnly = true)
    public PropertyPolicyVerdictListResponse get(Long memberId, Long planId, Long propertyId) {
        verifyOwnership(memberId, planId);
        if (!properties.existsByIdAndPlanId(propertyId, planId)) {
            throw new BusinessException(ErrorCode.PROPERTY_NOT_IN_PLAN);
        }
        Map<Long, Policy> byId = policyMasterById();
        List<PropertyPolicyVerdictResponse> results = verdicts.findByPropertyIdOrderByIdAsc(propertyId).stream()
                .map(verdict -> PropertyPolicyVerdictResponse.from(verdict, byId.get(verdict.getPolicyId())))
                .toList();
        return new PropertyPolicyVerdictListResponse(propertyId, results, null);
    }

    private PropertyPolicyVerdictResponse evaluateOne(
            Property property, PlanInput input, String policyCode, Instant now) {
        Policy policy = policies.findByCode(policyCode)
                .orElseThrow(() -> new IllegalStateException("시드에 있어야 할 정책이 없습니다: " + policyCode));
        Optional<PolicyRule> activeRule =
                rules.findFirstByPolicyIdAndStatusOrderByVersionDesc(policy.getId(), PolicyRuleStatus.ACTIVE);

        // 검수된 조건식이 없으면 판정하지 않는다. 없는 규칙으로 가능·불가를 단정하지 않는다.
        if (activeRule.isEmpty()) {
            return save(property, policy, null, PolicyVerdictResult.NEED_INFO, List.of("RULE_NOT_ACTIVE"), null, now);
        }

        List<ConditionResult> conditions = engine.evaluate(activeRule.get().getRuleJson(), input, property);
        // BR-12. 첫 실패에서 멈추지 않고 전부 모은다 — 무엇을 몇 개 고쳐야 하는지 알아야 한다.
        List<String> failCodes = conditions.stream()
                .filter(condition -> Boolean.FALSE.equals(condition.isMet()))
                .map(ConditionResult::code)
                .toList();
        List<String> pending = conditions.stream()
                .filter(condition -> condition.isMet() == null)
                .map(ConditionResult::code)
                .toList();

        PolicyVerdictResult status = !failCodes.isEmpty()
                ? PolicyVerdictResult.FAIL
                : pending.isEmpty() ? PolicyVerdictResult.PASS : PolicyVerdictResult.NEED_INFO;
        Short failStep = failCodes.stream()
                .map(FAIL_STEP_BY_CONDITION::get)
                .filter(java.util.Objects::nonNull)
                .min(Short::compareTo)
                .orElse(null);

        return save(property, policy, activeRule.get().getId(), status, failCodes, failStep, now);
    }

    /** 같은 매물·상품을 다시 판정하면 덮어쓴다. 이력이 아니라 지금 상태를 담는다. */
    private PropertyPolicyVerdictResponse save(
            Property property,
            Policy policy,
            Long ruleId,
            PolicyVerdictResult status,
            List<String> failCodes,
            Short failStep,
            Instant now) {
        PropertyPolicyVerdict verdict = verdicts.findByPropertyIdAndPolicyId(property.getId(), policy.getId())
                .map(existing -> {
                    existing.reevaluate(ruleId, status, failCodes, failStep, now);
                    return existing;
                })
                .orElseGet(() -> PropertyPolicyVerdict.create(
                        property.getId(), policy.getId(), ruleId, status, failCodes, failStep, now));
        return PropertyPolicyVerdictResponse.from(verdicts.save(verdict), policy);
    }

    private Map<Long, Policy> policyMasterById() {
        return policies.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Policy::getId, policy -> policy, (a, b) -> a));
    }

    private void verifyOwnership(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
    }
}
