package com.homerun.domain.settlement.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.settlement.dto.response.ReturnGuaranteeGuideResponse;
import com.homerun.domain.settlement.entity.LoanAccount;
import com.homerun.domain.settlement.repository.LoanAccountRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 반환보증 가입 안내 조회(FR-H1-01). 소유권 확인 후 실행 대출 담보로 분기한다. */
@Service
public class ReturnGuaranteeService {

    private final PlanRepository plans;
    private final LoanAccountRepository loans;
    private final ReturnGuaranteeAdvisor advisor;

    public ReturnGuaranteeService(PlanRepository plans, LoanAccountRepository loans, ReturnGuaranteeAdvisor advisor) {
        this.plans = plans;
        this.loans = loans;
        this.advisor = advisor;
    }

    @Transactional(readOnly = true)
    public ReturnGuaranteeGuideResponse forPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        LoanAccount loan =
                loans.findByPlanId(planId).orElseThrow(() -> new BusinessException(ErrorCode.LOAN_ACCOUNT_NOT_FOUND));
        return advisor.guide(loan.getGuarantee());
    }
}
