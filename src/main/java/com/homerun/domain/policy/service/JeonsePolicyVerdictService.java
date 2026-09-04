package com.homerun.domain.policy.service;

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
import com.homerun.domain.policy.entity.VerdictBasis;
import com.homerun.domain.policy.model.ConditionResult;
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
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전세대출 정책 자격 판정. {@link #evaluate}는 plan_input 기반(청년/일반 버팀목, 서울시 청년
 * 임차보증금 이자지원), {@link #evaluateReturnGuarantees}는 매물 기반(HUG/HF/SGI 반환보증,
 * #89)이다.
 *
 * <p>{@code #80} 의 {@link com.homerun.domain.loan.service.JeonseLoanDiagnosisService} 는
 * 그대로 둔다. 여기는 {@code policy_rule} 기반 새 판정 경로이고, 둘을 언제 합칠지는 별도 결정
 * 사항이다.
 */
@Service
public class JeonsePolicyVerdictService {

    private static final String ENGINE_VERSION = "policy-rule-v1";

    private static final List<String> JEONSE_POLICY_CODES =
            List.of("JEONSE-YOUTH-BEOTIMMOK", "JEONSE-GENERAL-BEOTIMMOK", "JEONSE-SEOUL-INTEREST-SUPPORT");

    private static final List<String> RETURN_GUARANTEE_POLICY_CODES =
            List.of("RETURN-GUARANTEE-HUG", "RETURN-GUARANTEE-HF", "RETURN-GUARANTEE-SGI");

    private final PlanRepository planRepository;
    private final PlanInputRepository planInputRepository;
    private final PropertyRepository propertyRepository;
    private final PolicyRepository policyRepository;
    private final PolicyRuleRepository policyRuleRepository;
    private final PolicyVerdictRepository policyVerdictRepository;
    private final VerdictBasisRepository verdictBasisRepository;
    private final PolicyRuleEngine engine;
    private final Clock clock;

    public JeonsePolicyVerdictService(
            PlanRepository planRepository,
            PlanInputRepository planInputRepository,
            PropertyRepository propertyRepository,
            PolicyRepository policyRepository,
            PolicyRuleRepository policyRuleRepository,
            PolicyVerdictRepository policyVerdictRepository,
            VerdictBasisRepository verdictBasisRepository,
            PolicyRuleEngine engine,
            Clock clock) {
        this.planRepository = planRepository;
        this.planInputRepository = planInputRepository;
        this.propertyRepository = propertyRepository;
        this.policyRepository = policyRepository;
        this.policyRuleRepository = policyRuleRepository;
        this.policyVerdictRepository = policyVerdictRepository;
        this.verdictBasisRepository = verdictBasisRepository;
        this.engine = engine;
        this.clock = clock;
    }

    @Transactional
    public JeonsePolicyVerdictListResponse evaluate(Long memberId, Long planId) {
        verifyJeonsePlan(memberId, planId);
        PlanInput input = planInputRepository
                .findByPlanId(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_INPUT_NOT_FOUND));

        List<PolicyVerdictResponse> results = JEONSE_POLICY_CODES.stream()
                .map(code -> evaluateOne(planId, code, input, null))
                .toList();

        return new JeonsePolicyVerdictListResponse(planId, results, Instant.now(clock));
    }

    /** 매물 기준 반환보증(HUG/HF/SGI) 판정. plan_input 은 필요 없다(#89). */
    @Transactional
    public JeonsePolicyVerdictListResponse evaluateReturnGuarantees(Long memberId, Long planId, Long propertyId) {
        verifyJeonsePlan(memberId, planId);
        Property property = propertyRepository
                .findByIdAndPlanId(propertyId, planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPERTY_NOT_IN_PLAN));

        List<PolicyVerdictResponse> results = RETURN_GUARANTEE_POLICY_CODES.stream()
                .map(code -> evaluateOne(planId, code, null, property))
                .toList();

        return new JeonsePolicyVerdictListResponse(planId, results, Instant.now(clock));
    }

    private void verifyJeonsePlan(Long memberId, Long planId) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        if (plan.getLeaseType() == LeaseType.WOLSE) {
            throw new BusinessException(ErrorCode.POLICY_JEONSE_PLAN_REQUIRED);
        }
    }

    private PolicyVerdictResponse evaluateOne(Long planId, String policyCode, PlanInput input, Property property) {
        Policy policy = policyRepository
                .findByCode(policyCode)
                .orElseThrow(() -> new IllegalStateException("시드에 있어야 할 정책이 없습니다: " + policyCode));

        Optional<PolicyRule> activeRule = policyRuleRepository.findFirstByPolicyIdAndStatusOrderByVersionDesc(
                policy.getId(), PolicyRuleStatus.ACTIVE);

        List<ConditionResult> conditionResults = activeRule.isEmpty()
                ? List.of(new ConditionResult(
                        "RULE_NOT_ACTIVE",
                        "정책 조건식 검수 대기",
                        "사람이 조건식을 검수해 ACTIVE 로 올려야 판정에 쓸 수 있습니다.",
                        null,
                        null,
                        policy.getDetailUrl()))
                : engine.evaluate(activeRule.get().getRuleJson(), input, property);

        PolicyVerdictResult verdict = aggregate(conditionResults);
        Long ruleId = activeRule.map(PolicyRule::getId).orElse(null);
        Long propertyId = property == null ? null : property.getId();

        PolicyVerdict saved = save(planId, policy.getId(), ruleId, propertyId, verdict, conditionResults);

        return new PolicyVerdictResponse(
                policy.getCode(), policy.getName(), saved.getVerdict(), toBasisResponses(conditionResults));
    }

    private PolicyVerdict save(
            Long planId,
            Long policyId,
            Long ruleId,
            Long propertyId,
            PolicyVerdictResult verdict,
            List<ConditionResult> conditions) {
        PolicyVerdict verdictEntity = policyVerdictRepository
                .findByPlanIdAndPolicyIdAndRuleId(planId, policyId, ruleId)
                .map(existing -> {
                    existing.reevaluate(verdict, propertyId, ENGINE_VERSION);
                    verdictBasisRepository.deleteByVerdictId(existing.getId());
                    return existing;
                })
                .orElseGet(() -> PolicyVerdict.create(planId, policyId, ruleId, propertyId, verdict, ENGINE_VERSION));
        PolicyVerdict saved = policyVerdictRepository.save(verdictEntity);

        conditions.forEach(condition -> verdictBasisRepository.save(VerdictBasis.create(
                saved.getId(),
                condition.code(),
                condition.label(),
                condition.requiredText(),
                condition.isMet(),
                condition.factCode(),
                condition.sourceUrl())));

        return saved;
    }

    private PolicyVerdictResult aggregate(List<ConditionResult> results) {
        if (results.stream().anyMatch(result -> Boolean.FALSE.equals(result.isMet()))) {
            return PolicyVerdictResult.FAIL;
        }
        if (results.stream().anyMatch(result -> result.isMet() == null)) {
            return PolicyVerdictResult.NEED_INFO;
        }
        return PolicyVerdictResult.PASS;
    }

    private List<ConditionBasisResponse> toBasisResponses(List<ConditionResult> results) {
        return results.stream().map(ConditionBasisResponse::from).toList();
    }
}
