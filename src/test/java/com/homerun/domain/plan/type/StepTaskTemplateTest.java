package com.homerun.domain.plan.type;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StepTaskTemplateTest {

    @Test
    void should_placePropertySafetyChecksOnFirstBase_forJeonseFlow() {
        assertThat(StepTaskTemplate.of(PlanGate.FIRST_DIAGNOSIS, LeaseType.JEONSE))
                .extracting(StepTaskTemplate::code)
                .containsSubsequence(
                        "REVIEW_DIAGNOSIS",
                        "LOAN_LIMIT_CHECK",
                        "PROPERTY_SEARCH",
                        "BUILDING_REGISTER_CHECK",
                        "ACTUAL_PRICE_CHECK",
                        "REGISTER_CHECK");
    }

    @Test
    void should_placeProductGuaranteeAndConsultationOnSecondBase() {
        assertThat(StepTaskTemplate.of(PlanGate.SECOND_POLICY_SELECTION, LeaseType.JEONSE))
                .extracting(StepTaskTemplate::code)
                .containsExactly(
                        "BANK_CONSULTATION",
                        "SELECT_LOAN_PRODUCT",
                        "SELECT_GUARANTEE",
                        "DOCUMENT_CHECK",
                        "RECORD_BANK_CONSULTATION");
    }

    @Test
    void should_receiveFixedDateBeforeLoanExecutionAndMoveInReport() {
        assertThat(StepTaskTemplate.of(PlanGate.THIRD_EXECUTION, LeaseType.JEONSE))
                .extracting(StepTaskTemplate::code)
                .containsSubsequence(
                        "DEPOSIT_PAYMENT", "FIXED_DATE", "APPLY_LOAN", "BALANCE_PAYMENT", "MOVE_IN_REPORT");
    }
}
