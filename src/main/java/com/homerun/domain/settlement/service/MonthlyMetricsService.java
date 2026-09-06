package com.homerun.domain.settlement.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.settlement.dto.request.MonthlyMetricsRequest;
import com.homerun.domain.settlement.dto.response.MonthlyMetricsResponse;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 월간 지표 조회(BR-28). 소유권 확인만 하고 계산은 {@link MonthlyMetricsCalculator} 에 맡긴다. */
@Service
public class MonthlyMetricsService {

    private final PlanRepository plans;
    private final MonthlyMetricsCalculator calculator;

    public MonthlyMetricsService(PlanRepository plans, MonthlyMetricsCalculator calculator) {
        this.plans = plans;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public MonthlyMetricsResponse forPlan(Long memberId, Long planId, MonthlyMetricsRequest request) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return calculator.calculate(
                request.loanAmount(),
                request.annualRatePercent(),
                request.managementFee(),
                request.monthlyIncome(),
                request.livingCost());
    }
}
