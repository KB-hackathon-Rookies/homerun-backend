package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.LienRepaymentResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 질권 상환 자금 흐름 조회(FR-H10-02). 소유권 확인 후 보증금·대출 잔액으로 분배를 계산한다. */
@Service
public class LienRepaymentService {

    private final PlanRepository plans;
    private final LienRepaymentAdvisor advisor;

    public LienRepaymentService(PlanRepository plans, LienRepaymentAdvisor advisor) {
        this.plans = plans;
        this.advisor = advisor;
    }

    @Transactional(readOnly = true)
    public LienRepaymentResponse guide(Long memberId, Long planId, long deposit, long loanBalance) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return advisor.guide(deposit, loanBalance);
    }
}
