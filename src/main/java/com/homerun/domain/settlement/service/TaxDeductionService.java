package com.homerun.domain.settlement.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.settlement.dto.request.TaxDeductionRequest;
import com.homerun.domain.settlement.dto.response.TaxDeductionResponse;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소득공제 조회(BR-29). 소유권 확인만 하고 계산은 {@link HousingTaxDeductionCalculator} 에 맡긴다.
 * 대출 데이터(loan_account)가 아직 없어 대출 조건은 요청으로 받는다.
 */
@Service
public class TaxDeductionService {

    private final PlanRepository plans;
    private final HousingTaxDeductionCalculator calculator;

    public TaxDeductionService(PlanRepository plans, HousingTaxDeductionCalculator calculator) {
        this.plans = plans;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public TaxDeductionResponse forPlan(Long memberId, Long planId, TaxDeductionRequest request) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return calculator.calculate(
                request.loanAmount(),
                request.annualRatePercent(),
                request.maturityLumpSum(),
                request.annualRepayment());
    }
}
