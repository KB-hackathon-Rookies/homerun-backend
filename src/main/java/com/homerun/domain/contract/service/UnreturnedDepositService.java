package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.UnreturnedDepositResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 보증금 미반환 대응 조회(FR-HX). 소유권 확인 후 반환보증 가입 여부로 분기한다. */
@Service
public class UnreturnedDepositService {

    private final PlanRepository plans;
    private final UnreturnedDepositAdvisor advisor;

    public UnreturnedDepositService(PlanRepository plans, UnreturnedDepositAdvisor advisor) {
        this.plans = plans;
        this.advisor = advisor;
    }

    @Transactional(readOnly = true)
    public UnreturnedDepositResponse guide(Long memberId, Long planId, boolean hasReturnGuarantee) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return advisor.guide(hasReturnGuarantee);
    }
}
