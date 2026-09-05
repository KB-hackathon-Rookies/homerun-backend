package com.homerun.domain.alternative.service;

import com.homerun.domain.alternative.dto.response.CauseResponse;
import com.homerun.domain.alternative.dto.response.CausesResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.policy.entity.Policy;
import com.homerun.domain.policy.entity.PolicyVerdict;
import com.homerun.domain.policy.entity.RejectionReason;
import com.homerun.domain.policy.repository.PolicyRepository;
import com.homerun.domain.policy.repository.PolicyVerdictRepository;
import com.homerun.domain.policy.repository.RejectionReasonRepository;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import com.homerun.domain.policy.type.RejectionReasonCategory;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 미충족 원인 분석(ALT-01-01). 새 판정을 하지 않는다 — 이미 저장된 FAIL {@code policy_verdict}
 * 와 그 {@code rejection_reason}을 계획 단위로 모아 정책 이름을 붙여 보여줄 뿐이다.
 *
 * <p>지금 FAIL 사유를 저장하는 건 전세 판정({@code JeonsePolicyVerdictService})뿐이라, 다른
 * 정책만 탈락한 계획은 원인이 빈 목록으로 나온다 — 특정 도메인을 하드코딩하지 않고 있는 데이터를
 * 그대로 비춘다.
 */
@Service
public class AlternativeCauseService {

    private final PlanRepository planRepository;
    private final PolicyVerdictRepository policyVerdictRepository;
    private final RejectionReasonRepository rejectionReasonRepository;
    private final PolicyRepository policyRepository;

    public AlternativeCauseService(
            PlanRepository planRepository,
            PolicyVerdictRepository policyVerdictRepository,
            RejectionReasonRepository rejectionReasonRepository,
            PolicyRepository policyRepository) {
        this.planRepository = planRepository;
        this.policyVerdictRepository = policyVerdictRepository;
        this.rejectionReasonRepository = rejectionReasonRepository;
        this.policyRepository = policyRepository;
    }

    public CausesResponse causes(Long memberId, Long planId) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);

        List<PolicyVerdict> failedVerdicts = policyVerdictRepository.findAllByPlanId(planId).stream()
                .filter(verdict -> verdict.getVerdict() == PolicyVerdictResult.FAIL)
                .toList();
        if (failedVerdicts.isEmpty()) {
            return new CausesResponse(planId, List.of());
        }

        Map<Long, Policy> policiesById =
                policyRepository
                        .findAllById(failedVerdicts.stream()
                                .map(PolicyVerdict::getPolicyId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Policy::getId, policy -> policy));

        List<CauseResponse> causes = new ArrayList<>();
        for (PolicyVerdict verdict : failedVerdicts) {
            Policy policy = policiesById.get(verdict.getPolicyId());
            if (policy == null) {
                continue;
            }
            for (RejectionReason reason : rejectionReasonRepository.findByVerdictId(verdict.getId())) {
                causes.add(toCause(policy, reason));
            }
        }
        return new CausesResponse(planId, causes);
    }

    private CauseResponse toCause(Policy policy, RejectionReason reason) {
        Policy alternative = reason.getAlternativeId() == null
                ? null
                : policyRepository.findById(reason.getAlternativeId()).orElse(null);
        return new CauseResponse(
                policy.getCode(),
                policy.getName(),
                reason.getReasonCode(),
                reason.getReasonLabel(),
                RejectionReasonCategory.from(reason.getReasonCode()),
                alternative == null ? null : alternative.getCode(),
                alternative == null ? null : alternative.getName());
    }
}
