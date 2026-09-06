package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.contract.type.CancelFeasibility;
import com.homerun.domain.contract.type.PaymentStage;
import com.homerun.domain.property.type.RejectionCategory;
import org.junit.jupiter.api.Test;

/** FR-P8-03·04·05. 단계별 해제 가능성, 특약 적용 여부, 특약 없을 때 잔여일 분기를 본다. */
class ContractCancellationAdvisorTest {

    private final ContractCancellationAdvisor advisor = new ContractCancellationAdvisor();

    // --- 해제 가능성(FR-P8-04) ---

    @Test
    void should_refundByPenalty_whenOnlyDownPayment() {
        var r = advisor.guide(PaymentStage.DOWN_PAYMENT, false, null, 30);
        assertThat(r.feasibility()).isEqualTo(CancelFeasibility.REFUNDABLE_BY_PENALTY);
    }

    @Test
    void should_needConsent_afterInterim() {
        var r = advisor.guide(PaymentStage.INTERIM, false, null, 30);
        assertThat(r.feasibility()).isEqualTo(CancelFeasibility.NEEDS_COUNTERPARTY_CONSENT);
    }

    @Test
    void should_beImpossible_afterBalance() {
        var r = advisor.guide(PaymentStage.BALANCE, false, null, 30);
        assertThat(r.feasibility()).isEqualTo(CancelFeasibility.NOT_POSSIBLE);
    }

    // --- 특약 발동(FR-P8-03) ---

    @Test
    void should_giveFourSteps_whenSpecialTerm() {
        var r = advisor.guide(PaymentStage.DOWN_PAYMENT, true, RejectionCategory.SUBJECT_ISSUE, null);
        assertThat(r.specialTermSteps()).hasSize(4);
        assertThat(r.specialTermApplicable()).isTrue();
        assertThat(r.remainingDayOptions()).isEmpty();
    }

    @Test
    void should_notApplySpecialTerm_forDocumentIssue() {
        // 서류 미비는 임차인 과실 여지가 있어 특약 적용이 어렵다.
        var r = advisor.guide(PaymentStage.DOWN_PAYMENT, true, RejectionCategory.DOCUMENT_ISSUE, null);
        assertThat(r.specialTermApplicable()).isFalse();
    }

    @Test
    void should_leaveApplicabilityNull_whenCategoryUnknown() {
        var r = advisor.guide(PaymentStage.DOWN_PAYMENT, true, null, null);
        assertThat(r.specialTermApplicable()).isNull();
    }

    // --- 특약 없을 때 잔여일 분기(FR-P8-05) ---

    @Test
    void should_suggestOtherBank_whenPlentyOfDays() {
        var r = advisor.guide(PaymentStage.DOWN_PAYMENT, false, null, 25);
        assertThat(r.remainingDayOptions()).anyMatch(o -> o.contains("다른 은행"));
        assertThat(r.specialTermSteps()).isEmpty();
    }

    @Test
    void should_suggestGuaranteeChange_inMiddleBand() {
        var r = advisor.guide(PaymentStage.DOWN_PAYMENT, false, null, 14);
        assertThat(r.remainingDayOptions()).anyMatch(o -> o.contains("보증기관"));
    }

    @Test
    void should_suggestAcceptLoss_whenFewDaysLeft() {
        // 경계: 7 미만이면 손실 감수. 6 은 손실 감수, 7 은 중간대.
        assertThat(advisor.guide(PaymentStage.DOWN_PAYMENT, false, null, 6).remainingDayOptions())
                .anyMatch(o -> o.contains("손실"));
        assertThat(advisor.guide(PaymentStage.DOWN_PAYMENT, false, null, 7).remainingDayOptions())
                .noneMatch(o -> o.contains("손실"));
    }
}
