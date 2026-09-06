package com.homerun.domain.policy.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.policy.dto.response.CollateralLoanLimitListResponse;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일반 전세대출의 담보별 한도 조회(BR-19). 소유권 확인과 입력 로드만 하고 계산은
 * {@link CollateralLoanLimitCalculator} 에 맡긴다.
 */
@Service
public class CollateralLoanLimitService {

    private final PlanRepository plans;
    private final PlanInputRepository inputs;
    private final CollateralLoanLimitCalculator calculator;

    public CollateralLoanLimitService(
            PlanRepository plans, PlanInputRepository inputs, CollateralLoanLimitCalculator calculator) {
        this.plans = plans;
        this.inputs = inputs;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public CollateralLoanLimitListResponse forPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        PlanInput input =
                inputs.findByPlanId(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_INPUT_NOT_FOUND));
        return calculator.calculate(input);
    }
}
