package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.contract.dto.response.LienRepaymentResponse;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.settlement.entity.LoanAccount;
import com.homerun.domain.settlement.repository.LoanAccountRepository;
import com.homerun.domain.settlement.type.RepaymentType;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 저장값에서 보증금·대출 잔액을 읽는 배선을 본다. override 우선, 만기일시=원금, 원리금균등=입력 필요. */
class LienRepaymentServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final LeaseContractRepository contracts = mock(LeaseContractRepository.class);
    private final LoanAccountRepository loans = mock(LoanAccountRepository.class);
    private final LienRepaymentService service =
            new LienRepaymentService(plans, contracts, loans, new LienRepaymentAdvisor());

    private void owned() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
    }

    private LoanAccount loan(RepaymentType type, long principal) {
        LoanAccount l = new LoanAccount(PLAN_ID);
        l.apply(
                ConsultedLoanProduct.YOUTH_BEOTIMMOK,
                null,
                principal,
                new BigDecimal("2.2"),
                type,
                LocalDate.of(2026, 5, 1),
                null,
                null,
                0);
        return l;
    }

    @Test
    void should_useOverrides_whenProvided() {
        owned();
        LienRepaymentResponse r = service.guide(MEMBER_ID, PLAN_ID, 180_000_000L, 144_000_000L);
        assertThat(r.toBank()).isEqualTo(144_000_000L);
        assertThat(r.toMe()).isEqualTo(36_000_000L);
    }

    @Test
    void should_readDepositFromContract_andBalanceFromLoan_whenMaturityLumpSum() {
        owned();
        LeaseContract contract = mock(LeaseContract.class);
        when(contract.getDeposit()).thenReturn(180_000_000L);
        when(contracts.findByPlanId(PLAN_ID)).thenReturn(Optional.of(contract));
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.of(loan(RepaymentType.MATURITY_LUMP_SUM, 144_000_000L)));

        LienRepaymentResponse r = service.guide(MEMBER_ID, PLAN_ID, null, null);

        assertThat(r.deposit()).isEqualTo(180_000_000L);
        assertThat(r.toBank()).isEqualTo(144_000_000L); // 만기일시: 잔액 = 원금
        assertThat(r.toMe()).isEqualTo(36_000_000L);
    }

    @Test
    void should_requireLoanBalance_forNonMaturityRepayment() {
        owned();
        LeaseContract contract = mock(LeaseContract.class);
        when(contract.getDeposit()).thenReturn(180_000_000L);
        when(contracts.findByPlanId(PLAN_ID)).thenReturn(Optional.of(contract));
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.of(loan(RepaymentType.EQUAL_INSTALLMENT, 144_000_000L)));

        assertThatThrownBy(() -> service.guide(MEMBER_ID, PLAN_ID, null, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.LOAN_BALANCE_REQUIRED));
    }

    @Test
    void should_throw_whenNoContractAndNoDepositOverride() {
        owned();
        when(contracts.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.guide(MEMBER_ID, PLAN_ID, null, 100_000_000L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONTRACT_NOT_FOUND));
    }
}
