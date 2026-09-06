package com.homerun.domain.settlement.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.settlement.dto.request.LoanAccountRequest;
import com.homerun.domain.settlement.dto.response.LoanAccountResponse;
import com.homerun.domain.settlement.entity.LoanAccount;
import com.homerun.domain.settlement.repository.LoanAccountRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 실행 대출 등록·조회(DR-20). 계획당 1건이라 등록은 있으면 덮어쓴다. */
@Service
public class LoanAccountService {

    private final PlanRepository plans;
    private final LoanAccountRepository loans;

    public LoanAccountService(PlanRepository plans, LoanAccountRepository loans) {
        this.plans = plans;
        this.loans = loans;
    }

    @Transactional
    public LoanAccountResponse register(Long memberId, Long planId, LoanAccountRequest request) {
        ownedPlan(memberId, planId);
        LoanAccount loan = loans.findByPlanId(planId).orElseGet(() -> new LoanAccount(planId));
        loan.apply(
                request.product(),
                request.guarantee(),
                request.principal(),
                request.rate(),
                request.repaymentType(),
                request.executedAt(),
                request.maturityAt(),
                request.preferentialUntil(),
                request.extensionCount() == null ? 0 : request.extensionCount());
        return LoanAccountResponse.from(loans.save(loan));
    }

    @Transactional(readOnly = true)
    public LoanAccountResponse get(Long memberId, Long planId) {
        ownedPlan(memberId, planId);
        return LoanAccountResponse.from(
                loans.findByPlanId(planId).orElseThrow(() -> new BusinessException(ErrorCode.LOAN_ACCOUNT_NOT_FOUND)));
    }

    private void ownedPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
    }
}
