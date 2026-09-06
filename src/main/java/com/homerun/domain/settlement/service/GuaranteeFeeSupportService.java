package com.homerun.domain.settlement.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.settlement.dto.request.GuaranteeFeeSupportAmountRequest;
import com.homerun.domain.settlement.dto.response.GuaranteeFeeSupportAmountResponse;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보증료 지원 금액 조회(BR-31). 소유권 확인만 하고 계산은 {@link GuaranteeFeeSupportCalculator} 에 맡긴다.
 */
@Service
public class GuaranteeFeeSupportService {

    private final PlanRepository plans;
    private final GuaranteeFeeSupportCalculator calculator;

    public GuaranteeFeeSupportService(PlanRepository plans, GuaranteeFeeSupportCalculator calculator) {
        this.plans = plans;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public GuaranteeFeeSupportAmountResponse forPlan(
            Long memberId, Long planId, GuaranteeFeeSupportAmountRequest request) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return calculator.calculate(request.category(), request.guaranteeFeePaid(), request.enrolledAt());
    }
}
