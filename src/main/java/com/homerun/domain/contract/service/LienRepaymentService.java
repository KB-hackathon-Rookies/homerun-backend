package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.LienRepaymentResponse;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.settlement.entity.LoanAccount;
import com.homerun.domain.settlement.repository.LoanAccountRepository;
import com.homerun.domain.settlement.type.RepaymentType;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 질권 상환 자금 흐름 조회(FR-H10-02). 소유권 확인 후 보증금·대출 잔액을 정해 분배를 계산한다.
 *
 * <p>보증금은 계약(lease_contract), 대출 잔액은 실행 대출(loan_account)에서 읽는다. 만기일시상환은
 * 원금이 곧 잔액이라 그대로 쓰고, 원리금균등은 잔액이 달라 직접 입력을 받는다. 값을 직접 넣으면
 * 그 값을 우선한다.
 */
@Service
public class LienRepaymentService {

    private final PlanRepository plans;
    private final LeaseContractRepository contracts;
    private final LoanAccountRepository loans;
    private final LienRepaymentAdvisor advisor;

    public LienRepaymentService(
            PlanRepository plans,
            LeaseContractRepository contracts,
            LoanAccountRepository loans,
            LienRepaymentAdvisor advisor) {
        this.plans = plans;
        this.contracts = contracts;
        this.loans = loans;
        this.advisor = advisor;
    }

    @Transactional(readOnly = true)
    public LienRepaymentResponse guide(Long memberId, Long planId, Long depositOverride, Long loanBalanceOverride) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);

        long deposit = depositOverride != null
                ? depositOverride
                : contracts
                        .findByPlanId(planId)
                        .map(c -> c.getDeposit())
                        .orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND));
        long loanBalance = loanBalanceOverride != null ? loanBalanceOverride : deriveLoanBalance(planId);
        return advisor.guide(deposit, loanBalance);
    }

    /** 만기일시상환은 원금이 곧 잔액이다. 원리금균등 등은 잔액이 달라 직접 입력을 받는다. */
    private long deriveLoanBalance(Long planId) {
        LoanAccount loan =
                loans.findByPlanId(planId).orElseThrow(() -> new BusinessException(ErrorCode.LOAN_ACCOUNT_NOT_FOUND));
        if (loan.getRepaymentType() != RepaymentType.MATURITY_LUMP_SUM) {
            throw new BusinessException(ErrorCode.LOAN_BALANCE_REQUIRED);
        }
        return loan.getPrincipal();
    }
}
