package com.homerun.domain.policy.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.policy.dto.request.CostSimulationRequest;
import com.homerun.domain.policy.dto.response.CostSimulationResponse;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부대비용·총비용 시뮬레이션 조회(BR-08a·BR-21). 소유권 확인과 보증금 로드만 하고 계산은
 * {@link AncillaryCostCalculator} 에 맡긴다.
 */
@Service
public class CostSimulationService {

    private final PlanRepository plans;
    private final PlanInputRepository inputs;
    private final AncillaryCostCalculator calculator;

    public CostSimulationService(PlanRepository plans, PlanInputRepository inputs, AncillaryCostCalculator calculator) {
        this.plans = plans;
        this.inputs = inputs;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public CostSimulationResponse simulate(Long memberId, Long planId, CostSimulationRequest request) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        PlanInput input =
                inputs.findByPlanId(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_INPUT_NOT_FOUND));
        Long deposit = input.getHopeDeposit();
        if (deposit == null) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_INPUT_REQUIRED);
        }

        return new CostSimulationResponse(
                deposit,
                request.loanAmount(),
                calculator.ancillaryCost(
                        deposit, request.loanAmount(), request.collateral(), request.apartment(), request.movingCost()),
                calculator.totalCostYear1(
                        request.loanAmount(), request.annualRatePercent(), request.collateral(), request.apartment()));
    }
}
