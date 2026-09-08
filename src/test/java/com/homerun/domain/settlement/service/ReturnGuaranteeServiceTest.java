package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.settlement.dto.response.ReturnGuaranteeGuideResponse;
import com.homerun.domain.settlement.entity.LoanAccount;
import com.homerun.domain.settlement.repository.LoanAccountRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 소유권·대출 존재 검증 후 담보 방식으로 분기 위임을 본다. 안내는 ReturnGuaranteeAdvisor 몫. */
class ReturnGuaranteeServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final LoanAccountRepository loans = mock(LoanAccountRepository.class);
    private final ReturnGuaranteeAdvisor advisor = mock(ReturnGuaranteeAdvisor.class);
    private final ReturnGuaranteeService service = new ReturnGuaranteeService(plans, loans, advisor);

    private void owned() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
    }

    @Test
    void should_guideByGuaranteeMethod_whenOwnedWithLoan() {
        owned();
        LoanAccount loan = mock(LoanAccount.class);
        when(loan.getGuarantee()).thenReturn(CollateralMethod.HUG_SAFE_JEONSE);
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.of(loan));
        ReturnGuaranteeGuideResponse expected = mock(ReturnGuaranteeGuideResponse.class);
        when(advisor.guide(CollateralMethod.HUG_SAFE_JEONSE)).thenReturn(expected);

        assertThat(service.forPlan(MEMBER_ID, PLAN_ID)).isSameAs(expected);
    }

    @Test
    void should_throwLoanNotFound_whenNoLoanExecuted() {
        owned();
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.forPlan(MEMBER_ID, PLAN_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOAN_ACCOUNT_NOT_FOUND);
    }

    @Test
    void should_throwAccessDenied_whenNotOwner() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(999L, LeaseType.JEONSE, null)));
        assertThatThrownBy(() -> service.forPlan(MEMBER_ID, PLAN_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLAN_ACCESS_DENIED);
    }
}
