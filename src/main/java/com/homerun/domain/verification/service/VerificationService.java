package com.homerun.domain.verification.service;

import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.policy.entity.Policy;
import com.homerun.domain.policy.entity.PolicyVerdict;
import com.homerun.domain.policy.entity.VerdictBasis;
import com.homerun.domain.policy.repository.PolicyRepository;
import com.homerun.domain.policy.repository.PolicyVerdictRepository;
import com.homerun.domain.policy.repository.VerdictBasisRepository;
import com.homerun.domain.verification.dto.response.VerificationDtos.PendingCondition;
import com.homerun.domain.verification.dto.response.VerificationDtos.PendingConditionList;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추가 확인 관리(VER-01).
 *
 * <p>2루 판정에서 NEED_INFO 로 빠진 조건은 판정 시점에 {@code verdict_basis} 로 이미 저장돼
 * 있다({@code is_met IS NULL}). 여기서는 새로 계산하지 않고 계획(plan) 단위로 모아 보여만 준다
 * (VER-01-01). {@code required_text}·{@code source_url} 은 판정 근거에 이미 있어서 그대로
 * 확인 방법 안내로 쓴다(VER-01-02) — 근거 없는 안내를 지어내지 않는다(NFR-01-06).
 */
@Service
public class VerificationService {

    private static final Comparator<PendingCondition> ORDER =
            Comparator.comparing(PendingCondition::policyCode).thenComparing(PendingCondition::conditionCode);

    private final PlanRepository plans;
    private final PolicyVerdictRepository verdicts;
    private final VerdictBasisRepository basisRepository;
    private final PolicyRepository policies;

    public VerificationService(
            PlanRepository plans,
            PolicyVerdictRepository verdicts,
            VerdictBasisRepository basisRepository,
            PolicyRepository policies) {
        this.plans = plans;
        this.verdicts = verdicts;
        this.basisRepository = basisRepository;
        this.policies = policies;
    }

    /** 남의 계획을 건드리지 못하게 한다(SEC-01-04). */
    private void verifyOwner(Long memberId, Long planId) {
        plans.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND))
                .verifyOwner(memberId);
    }

    @Transactional(readOnly = true)
    public PendingConditionList listPending(Long memberId, Long planId) {
        verifyOwner(memberId, planId);

        List<PolicyVerdict> planVerdicts = verdicts.findAllByPlanId(planId);
        if (planVerdicts.isEmpty()) {
            return new PendingConditionList(List.of());
        }

        Map<Long, PolicyVerdict> verdictsById =
                planVerdicts.stream().collect(Collectors.toMap(PolicyVerdict::getId, Function.identity()));
        List<Long> verdictIds = planVerdicts.stream().map(PolicyVerdict::getId).toList();
        List<VerdictBasis> pending = basisRepository.findByVerdictIdInAndMetIsNull(verdictIds);
        if (pending.isEmpty()) {
            return new PendingConditionList(List.of());
        }

        Map<Long, Policy> policiesById =
                policies
                        .findAllById(planVerdicts.stream()
                                .map(PolicyVerdict::getPolicyId)
                                .distinct()
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Policy::getId, Function.identity()));

        List<PendingCondition> conditions = pending.stream()
                .map(basis -> {
                    PolicyVerdict verdict = verdictsById.get(basis.getVerdictId());
                    Policy policy = policiesById.get(verdict.getPolicyId());
                    return PendingCondition.from(policy.getCode(), policy.getName(), basis);
                })
                .sorted(ORDER)
                .toList();

        return new PendingConditionList(conditions);
    }
}
