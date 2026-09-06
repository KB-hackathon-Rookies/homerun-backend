package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.contract.dto.response.LienRepaymentResponse;
import org.junit.jupiter.api.Test;

/** FR-H10-02. 은행 몫=min(대출 잔액, 보증금), 내 몫=나머지. */
class LienRepaymentAdvisorTest {

    private final LienRepaymentAdvisor advisor = new LienRepaymentAdvisor();

    @Test
    void should_splitByLoanBalance() {
        // 정하은: 보증금 1.8억, 대출 1.44억 → 은행 1.44억, 나 3,600만.
        LienRepaymentResponse r = advisor.guide(180_000_000L, 144_000_000L);
        assertThat(r.toBank()).isEqualTo(144_000_000L);
        assertThat(r.toMe()).isEqualTo(36_000_000L);
    }

    @Test
    void should_capBankShareAtDeposit_whenLoanExceedsDeposit() {
        // 대출이 보증금보다 크면 은행이 보증금 전부, 내 몫 0.
        LienRepaymentResponse r = advisor.guide(100_000_000L, 120_000_000L);
        assertThat(r.toBank()).isEqualTo(100_000_000L);
        assertThat(r.toMe()).isZero();
    }

    @Test
    void should_giveAllToMe_whenNoLoan() {
        LienRepaymentResponse r = advisor.guide(180_000_000L, 0L);
        assertThat(r.toBank()).isZero();
        assertThat(r.toMe()).isEqualTo(180_000_000L);
    }

    @Test
    void should_warnAboutDirectRemittanceAndMistakenReceipt() {
        LienRepaymentResponse r = advisor.guide(180_000_000L, 144_000_000L);
        assertThat(r.notes()).anyMatch(n -> n.contains("직접 송금"));
        assertThat(r.notes()).anyMatch(n -> n.contains("즉시 은행에 반환"));
    }
}
