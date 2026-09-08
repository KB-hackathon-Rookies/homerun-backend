package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.settlement.dto.response.PostAssetReviewResponse;
import com.homerun.domain.settlement.entity.LoanAccount;
import com.homerun.domain.settlement.repository.LoanAccountRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 소유권·대출 존재 검증 후 실행 상품으로 분기 위임을 본다. 안내는 PostAssetReviewAdvisor 몫. */
class PostAssetReviewServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final LoanAccountRepository loans = mock(LoanAccountRepository.class);
    private final PostAssetReviewAdvisor advisor = mock(PostAssetReviewAdvisor.class);
    private final PostAssetReviewService service = new PostAssetReviewService(plans, loans, advisor);

    private void owned() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
    }

    @Test
    void should_guideByLoanProduct_whenOwnedWithLoan() {
        owned();
        LoanAccount loan = mock(LoanAccount.class);
        when(loan.getProduct()).thenReturn(ConsultedLoanProduct.YOUTH_BEOTIMMOK);
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.of(loan));
        PostAssetReviewResponse expected = mock(PostAssetReviewResponse.class);
        when(advisor.guide(ConsultedLoanProduct.YOUTH_BEOTIMMOK)).thenReturn(expected);

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
