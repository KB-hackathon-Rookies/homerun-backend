package com.homerun.domain.policy.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.policy.dto.response.ConditionBasisResponse;
import com.homerun.domain.policy.dto.response.PolicyVerdictResponse;
import com.homerun.domain.policy.entity.Policy;
import com.homerun.domain.policy.entity.PolicyRule;
import com.homerun.domain.policy.entity.PolicyVerdict;
import com.homerun.domain.policy.entity.VerdictBasis;
import com.homerun.domain.policy.model.ConditionResult;
import com.homerun.domain.policy.model.SavedVerdict;
import com.homerun.domain.policy.repository.PolicyRepository;
import com.homerun.domain.policy.repository.PolicyRuleRepository;
import com.homerun.domain.policy.repository.PolicyVerdictRepository;
import com.homerun.domain.policy.repository.VerdictBasisRepository;
import com.homerun.domain.policy.type.PolicyRuleStatus;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 청년미래적금 자격 판정(POL-02-02, #116). 전세·월세 어느 쪽 계획이든 상관없다 — 대출이 아니라
 * 적금이라 {@link JeonsePolicyVerdictService}의 lease_type 제한이 안 붙는다. 매물·대안정책
 * 개념도 없어(적금은 한 종류뿐) 예상 대출 스펙·rejection reason 로직 없이 조건 판정만 한다.
 *
 * <p>정책 조건에 가구소득·최근 3년 금융소득종합과세 이력이 껴 있는데 둘 다 plan_input 이 안
 * 걷는 값이라 항상 NEED_INFO 다 — 그래서 이 정책은 사실상 PASS 가 안 나온다. 의도된 설계다
 * (NFR-01-06: 확인 안 된 게이트를 빼고 통과를 주지 않는다).
 */
@Service
public class YouthSavingsVerdictService {

    private static final String ENGINE_VERSION = "policy-rule-v1";
    private static final String POLICY_CODE = "YOUTH-FUTURE-SAVINGS";

    private final PlanRepository planRepository;
    private final PlanInputRepository planInputRepository;
    private final PolicyRepository policyRepository;
    private final PolicyRuleRepository policyRuleRepository;
    private final PolicyVerdictRepository policyVerdictRepository;
    private final VerdictBasisRepository verdictBasisRepository;
    private final PolicyRuleEngine engine;
    private final Clock clock;

    public YouthSavingsVerdictService(
            PlanRepository planRepository,
            PlanInputRepository planInputRepository,
            PolicyRepository policyRepository,
            PolicyRuleRepository policyRuleRepository,
            PolicyVerdictRepository policyVerdictRepository,
            VerdictBasisRepository verdictBasisRepository,
            PolicyRuleEngine engine,
            Clock clock) {
        this.planRepository = planRepository;
        this.planInputRepository = planInputRepository;
        this.policyRepository = policyRepository;
        this.policyRuleRepository = policyRuleRepository;
        this.policyVerdictRepository = policyVerdictRepository;
        this.verdictBasisRepository = verdictBasisRepository;
        this.engine = engine;
        this.clock = clock;
    }

    @Transactional
    public PolicyVerdictResponse evaluate(Long memberId, Long planId) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        PlanInput input = planInputRepository
                .findByPlanId(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_INPUT_NOT_FOUND));

        Policy policy = policyRepository
                .findByCode(POLICY_CODE)
                .orElseThrow(() -> new IllegalStateException("시드에 있어야 할 정책이 없습니다: " + POLICY_CODE));

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
                : engine.evaluate(activeRule.get().getRuleJson(), input);

        PolicyVerdictResult verdict = aggregate(conditionResults);
        Long ruleId = activeRule.map(PolicyRule::getId).orElse(null);
        SavedVerdict saved = save(planId, policy.getId(), ruleId, verdict, conditionResults);

        return new PolicyVerdictResponse(
                policy.getCode(),
                policy.getName(),
                saved.verdict().getVerdict(),
                activeRule.map(PolicyRule::getVersion).orElse(null),
                toBasisResponses(conditionResults),
                missingFields(conditionResults),
                List.of(),
                null,
                saved.previousVerdict(),
                saved.changedConditionCodes());
    }

    /** NEED_INFO 로 빠진 조건 코드들 — API 공통계약의 missingFields[]. */
    private List<String> missingFields(List<ConditionResult> conditionResults) {
        return conditionResults.stream()
                .filter(result -> result.isMet() == null)
                .map(ConditionResult::code)
                .toList();
    }

    private SavedVerdict save(
            Long planId, Long policyId, Long ruleId, PolicyVerdictResult verdict, List<ConditionResult> conditions) {
        Optional<PolicyVerdict> existing =
                policyVerdictRepository.findByPlanIdAndPolicyIdAndRuleId(planId, policyId, ruleId);

        // 덮어쓰기 전에 직전 상태를 남긴다(VER-01-05). Collectors.toMap 은 value 가 null 이면
        // 던지므로(NEED_INFO 조건의 isMet=null) put 으로 직접 채운다.
        PolicyVerdictResult previousVerdict =
                existing.map(PolicyVerdict::getVerdict).orElse(null);
        Map<String, Boolean> previousMetByCode = new HashMap<>();
        existing.ifPresent(e -> verdictBasisRepository
                .findByVerdictId(e.getId())
                .forEach(basis -> previousMetByCode.put(basis.getConditionCode(), basis.getMet())));

        PolicyVerdict verdictEntity = existing.map(e -> {
                    e.reevaluate(verdict, null, null, null, ENGINE_VERSION);
                    verdictBasisRepository.deleteByVerdictId(e.getId());
                    return e;
                })
                .orElseGet(() ->
                        PolicyVerdict.create(planId, policyId, ruleId, null, verdict, null, null, ENGINE_VERSION));
        PolicyVerdict saved = policyVerdictRepository.save(verdictEntity);

        conditions.forEach(condition -> verdictBasisRepository.save(VerdictBasis.create(
                saved.getId(),
                condition.code(),
                condition.label(),
                condition.requiredText(),
                condition.isMet(),
                condition.factCode(),
                condition.sourceUrl())));

        List<String> changedConditionCodes = conditions.stream()
                .filter(condition -> previousMetByCode.containsKey(condition.code())
                        && !Objects.equals(previousMetByCode.get(condition.code()), condition.isMet()))
                .map(ConditionResult::code)
                .toList();

        return new SavedVerdict(saved, previousVerdict, changedConditionCodes);
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
        LocalDate today = LocalDate.now(clock);
        return results.stream()
                .map(result -> ConditionBasisResponse.from(result, today))
                .toList();
    }
}
