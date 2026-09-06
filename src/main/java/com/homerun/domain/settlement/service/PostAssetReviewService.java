package com.homerun.domain.settlement.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.settlement.dto.response.PostAssetReviewResponse;
import com.homerun.domain.settlement.entity.LoanAccount;
import com.homerun.domain.settlement.repository.LoanAccountRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사후자산심사 안내 조회(FR-H3-01). 소유권 확인 후 실행 대출 상품으로 분기한다. */
@Service
public class PostAssetReviewService {

    private final PlanRepository plans;
    private final LoanAccountRepository loans;
    private final PostAssetReviewAdvisor advisor;

    public PostAssetReviewService(PlanRepository plans, LoanAccountRepository loans, PostAssetReviewAdvisor advisor) {
        this.plans = plans;
        this.loans = loans;
        this.advisor = advisor;
    }

    @Transactional(readOnly = true)
    public PostAssetReviewResponse forPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        LoanAccount loan =
                loans.findByPlanId(planId).orElseThrow(() -> new BusinessException(ErrorCode.LOAN_ACCOUNT_NOT_FOUND));
        return advisor.guide(loan.getProduct());
    }
}
