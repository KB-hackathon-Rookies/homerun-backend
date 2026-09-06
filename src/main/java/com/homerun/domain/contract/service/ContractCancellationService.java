package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.request.ContractCancellationRequest;
import com.homerun.domain.contract.dto.response.ContractCancellationResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 계약 해제 대응 조회(FR-P8). 소유권 확인만 하고 안내는 {@link ContractCancellationAdvisor} 에 맡긴다. */
@Service
public class ContractCancellationService {

    private final PlanRepository plans;
    private final ContractCancellationAdvisor advisor;

    public ContractCancellationService(PlanRepository plans, ContractCancellationAdvisor advisor) {
        this.plans = plans;
        this.advisor = advisor;
    }

    @Transactional(readOnly = true)
    public ContractCancellationResponse guide(Long memberId, Long planId, ContractCancellationRequest request) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return advisor.guide(
                request.paymentStage(), request.hasSpecialTerm(), request.rejectionCategory(), request.daysToBalance());
    }
}
