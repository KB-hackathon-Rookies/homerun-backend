package com.homerun.domain.plan.type;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StepTaskTemplateTest {

    @Test
    void should_keepFirstBaseIndependentFromProperty_forJeonseFlow() {
        assertThat(StepTaskTemplate.of(PlanGate.FIRST_DIAGNOSIS, LeaseType.JEONSE))
                .extracting(StepTaskTemplate::code)
                .containsExactly(
                        "INPUT_INCOME_ASSET", "INPUT_HOUSING_CONDITION", "REVIEW_DIAGNOSIS", "LOAN_LIMIT_CHECK");
    }

    @Test
    void should_placePropertyAndDecisionFlowOnSecondBase() {
        assertThat(StepTaskTemplate.of(PlanGate.SECOND_POLICY_SELECTION, LeaseType.JEONSE))
                .extracting(StepTaskTemplate::code)
                .containsExactly(
                        "PROPERTY_SEARCH",
                        "BUILDING_REGISTER_CHECK",
                        "ACTUAL_PRICE_CHECK",
                        "REGISTER_CHECK",
                        "BANK_CONSULTATION",
                        "SELECT_LOAN_PRODUCT",
                        "SELECT_GUARANTEE",
                        "DOCUMENT_CHECK",
                        "RECORD_BANK_CONSULTATION");
    }

    @Test
    void should_markMonthlyHomeTaskAsRecurring() {
        assertThat(StepTaskTemplate.FIRST_MONTH_CHECKIN.recurrence()).isEqualTo(TaskRecurrence.MONTHLY);
    }

    @Test
    void should_receiveFixedDateBeforeLoanExecutionAndMoveInReport() {
        assertThat(StepTaskTemplate.of(PlanGate.THIRD_EXECUTION, LeaseType.JEONSE))
                .extracting(StepTaskTemplate::code)
                .containsSubsequence(
                        "DEPOSIT_PAYMENT", "FIXED_DATE", "APPLY_LOAN", "BALANCE_PAYMENT", "MOVE_IN_REPORT");
    }
}
