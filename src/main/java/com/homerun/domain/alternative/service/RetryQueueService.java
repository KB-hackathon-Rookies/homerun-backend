package com.homerun.domain.alternative.service;

import com.homerun.domain.alternative.dto.response.RetryQueueItemResponse;
import com.homerun.domain.alternative.dto.response.RetryQueueResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.policy.entity.Policy;
import com.homerun.domain.policy.entity.PolicyRule;
import com.homerun.domain.policy.model.AgeEligibilityGap;
import com.homerun.domain.policy.model.RuleCondition;
import com.homerun.domain.policy.repository.PolicyRepository;
import com.homerun.domain.policy.repository.PolicyRuleRepository;
import com.homerun.domain.policy.service.PolicyRuleEngine;
import com.homerun.domain.policy.type.PolicyRuleStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 재도전 큐(ALT-01-04). 나이 하한을 아직 못 채워 실패·미확인으로 남는 조건 중, 생년월일처럼
 * 확정된 값으로 채우는 날짜를 계산할 수 있는 것만 다룬다. 소득·자산·재직형태처럼 언제 바뀔지
 * 알 수 없는 조건은 넣지 않는다 — 근거 없는 예측이 곧 틀린 안내다(NFR-01-06 원칙과 같은 이유).
 *
 * <p>큐를 저장하지 않고 매 요청마다 다시 계산한다 — 정책 수치가 바뀌면 즉시 반영된다.
 */
@Service
public class RetryQueueService {

    private final PlanRepository planRepository;
    private final PlanInputRepository planInputRepository;
    private final PolicyRepository policyRepository;
    private final PolicyRuleRepository policyRuleRepository;
    private final PolicyRuleEngine engine;
    private final Clock clock;

    public RetryQueueService(
            PlanRepository planRepository,
            PlanInputRepository planInputRepository,
            PolicyRepository policyRepository,
            PolicyRuleRepository policyRuleRepository,
            PolicyRuleEngine engine,
            Clock clock) {
        this.planRepository = planRepository;
        this.planInputRepository = planInputRepository;
        this.policyRepository = policyRepository;
        this.policyRuleRepository = policyRuleRepository;
        this.engine = engine;
        this.clock = clock;
    }

    public RetryQueueResponse build(Long memberId, Long planId) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);

        PlanInput input = planInputRepository.findByPlanId(planId).orElse(null);
        if (input == null || input.getBirthDate() == null) {
            // 생년월일을 모르면 채우는 날짜 자체를 계산할 수 없다 — 빈 큐가 맞는 답이다.
            return new RetryQueueResponse(planId, List.of());
        }

        List<PolicyRule> activeRules = latestActiveRulePerPolicy();
        Map<Long, Policy> policiesById =
                policyRepository
                        .findAllById(activeRules.stream()
                                .map(PolicyRule::getPolicyId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Policy::getId, p -> p));

        List<RetryQueueItemResponse> items = new ArrayList<>();
        for (PolicyRule rule : activeRules) {
            Policy policy = policiesById.get(rule.getPolicyId());
            if (policy == null) {
                continue;
            }
            for (RuleCondition condition : rule.getRuleJson().conditions()) {
                engine.upcomingAgeEligibility(condition, input).ifPresent(gap -> items.add(toItem(policy, gap)));
            }
        }
        items.sort(Comparator.comparing(RetryQueueItemResponse::eligibleFrom));
        return new RetryQueueResponse(planId, items);
    }

    private RetryQueueItemResponse toItem(Policy policy, AgeEligibilityGap gap) {
        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(clock), gap.eligibleFrom());
        return new RetryQueueItemResponse(
                policy.getCode(), policy.getName(), gap.label(), gap.eligibleFrom(), daysRemaining, gap.sourceUrl());
    }

    /** 정책 하나에 ACTIVE 버전이 여러 개 잡혀 있어도(테스트 등) 최신 버전만 남긴다 —
     * findFirstByPolicyIdAndStatusOrderByVersionDesc 와 같은 선택 규칙을 여기서도 지킨다. */
    private List<PolicyRule> latestActiveRulePerPolicy() {
        Map<Long, PolicyRule> latest = new HashMap<>();
        for (PolicyRule rule : policyRuleRepository.findAllByStatus(PolicyRuleStatus.ACTIVE)) {
            latest.merge(rule.getPolicyId(), rule, (a, b) -> a.getVersion() >= b.getVersion() ? a : b);
        }
        return new ArrayList<>(latest.values());
    }
}
