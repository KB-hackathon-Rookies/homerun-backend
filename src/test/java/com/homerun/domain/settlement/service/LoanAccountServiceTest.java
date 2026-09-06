package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.settlement.dto.request.LoanAccountRequest;
import com.homerun.domain.settlement.entity.LoanAccount;
import com.homerun.domain.settlement.repository.LoanAccountRepository;
import com.homerun.domain.settlement.type.RepaymentType;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 계획당 1건 덮어쓰기와 미등록 조회 예외를 본다. */
class LoanAccountServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final LoanAccountRepository loans = mock(LoanAccountRepository.class);
    private final LoanAccountService service = new LoanAccountService(plans, loans);

    private void givenOwnedPlan() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        when(loans.save(any(LoanAccount.class))).thenAnswer(i -> i.getArgument(0));
    }

    private LoanAccountRequest request(long principal) {
        return new LoanAccountRequest(
                ConsultedLoanProduct.YOUTH_BEOTIMMOK,
                null,
                principal,
                new BigDecimal("2.2"),
                RepaymentType.MATURITY_LUMP_SUM,
                null,
                null,
                null,
                null);
    }

    @Test
    void should_overwriteExisting_when_registeringAgain() {
        givenOwnedPlan();
        LoanAccount existing = new LoanAccount(PLAN_ID);
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.of(existing));

        var r = service.register(MEMBER_ID, PLAN_ID, request(200_000_000L));

        // 새 엔티티를 만들지 않고 기존 것을 덮어쓴다.
        assertThat(r.principal()).isEqualTo(200_000_000L);
        assertThat(existing.getPrincipal()).isEqualTo(200_000_000L);
    }

    @Test
    void should_defaultExtensionCountToZero_when_null() {
        givenOwnedPlan();
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        var r = service.register(MEMBER_ID, PLAN_ID, request(100_000_000L));

        assertThat(r.extensionCount()).isZero();
    }

    @Test
    void should_throw_when_gettingUnregisteredLoan() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(MEMBER_ID, PLAN_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.LOAN_ACCOUNT_NOT_FOUND));
    }

    @Test
    void should_throw_when_notOwner() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));

        assertThatThrownBy(() -> service.get(999L, PLAN_ID)).isInstanceOf(BusinessException.class);
    }
}
