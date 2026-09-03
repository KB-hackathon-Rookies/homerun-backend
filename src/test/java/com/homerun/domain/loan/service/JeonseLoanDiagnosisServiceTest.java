package com.homerun.domain.loan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.loan.dto.response.JeonseLoanDiagnosisResponse;
import com.homerun.domain.loan.type.JeonseLoanProduct;
import com.homerun.domain.loan.type.LoanDiagnosisStatus;
import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.FinancialValueSource;
import com.homerun.domain.plan.type.HouseholderStatus;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JeonseLoanDiagnosisServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC);

    private final PlanRepository plans = mock(PlanRepository.class);
    private final PlanInputRepository inputs = mock(PlanInputRepository.class);
    private final FactRegistry facts = mock(FactRegistry.class);
    private final JeonseLoanDiagnosisService service = new JeonseLoanDiagnosisService(plans, inputs, facts, CLOCK);

    @BeforeEach
    void setUpFacts() {
        when(facts.won("FCT-003")).thenReturn(50_000_000L);
        when(facts.won("FCT-170")).thenReturn(345_000_000L);
        when(facts.won("FCT-171")).thenReturn(150_000_000L);
        when(facts.won("FCT-175")).thenReturn(300_000_000L);
        when(facts.require("FCT-008")).thenReturn(fact("FCT-008", "80"));
        when(facts.require("FCT-172")).thenReturn(fact("FCT-172", "2.2"));
        when(facts.require("FCT-173")).thenReturn(fact("FCT-173", "3.3"));
        when(facts.require("FCT-174")).thenReturn(fact("FCT-174", "34"));
    }

    @Test
    void should_calculateYouthLoanSpec_when_requiredInputsAreConfirmed() {
        givenPlan(LeaseType.JEONSE, input(3_000_000L, true));

        JeonseLoanDiagnosisResponse result = service.diagnose(MEMBER_ID, PLAN_ID);

        var youth = result.products().stream()
                .filter(product -> product.product() == JeonseLoanProduct.YOUTH_BEOTIMMOK)
                .findFirst()
                .orElseThrow();
        assertThat(youth.status()).isEqualTo(LoanDiagnosisStatus.EXPECTED_ELIGIBLE);
        assertThat(youth.estimatedLoanAmount()).isEqualTo(150_000_000L);
        assertThat(youth.ownFundsRequired()).isEqualTo(50_000_000L);
        assertThat(youth.monthlyInterestMin()).isEqualTo(275_000L);
        assertThat(youth.monthlyInterestMax()).isEqualTo(412_500L);
        assertThat(result.recommendedDepositLimit()).isEqualTo(200_000_000L);
    }

    @Test
    void should_rejectYouthProduct_when_annualIncomeExceedsBoundary() {
        givenPlan(LeaseType.JEONSE, input(4_166_667L, true));

        var youth = service.diagnose(MEMBER_ID, PLAN_ID).products().get(0);

        assertThat(youth.status()).isEqualTo(LoanDiagnosisStatus.INELIGIBLE);
        assertThat(youth.reasons()).anyMatch(reason -> reason.contains("연소득"));
        assertThat(youth.estimatedLoanAmount()).isNull();
    }

    @Test
    void should_requireReview_when_openBankingValueIsNotConfirmed() {
        givenPlan(LeaseType.JEONSE, input(3_000_000L, false));

        var youth = service.diagnose(MEMBER_ID, PLAN_ID).products().get(0);

        assertThat(youth.status()).isEqualTo(LoanDiagnosisStatus.REVIEW_REQUIRED);
        assertThat(youth.reasons()).anyMatch(reason -> reason.contains("금융정보"));
    }

    @Test
    void should_rejectDiagnosis_when_planIsPureMonthlyRent() {
        givenPlan(LeaseType.WOLSE, input(3_000_000L, true));

        assertThatThrownBy(() -> service.diagnose(MEMBER_ID, PLAN_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.JEONSE_PLAN_REQUIRED));
    }

    private void givenPlan(LeaseType leaseType, PlanInput input) {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, leaseType, null)));
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));
    }

    private PlanInput input(long monthlyIncome, boolean confirmed) {
        return PlanInput.create(
                PLAN_ID,
                new PlanInputRequest(
                        200_000_000L,
                        20_000_000L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        HouseholderStatus.CURRENT,
                        MaritalStatus.SINGLE,
                        EmploymentType.FULL_TIME,
                        24,
                        CompanySize.SMALL,
                        true,
                        LocalDate.of(2000, 1, 1),
                        0,
                        monthlyIncome,
                        100_000_000L,
                        50_000_000L,
                        false,
                        FinancialValueSource.OPEN_BANKING,
                        FinancialValueSource.OPEN_BANKING,
                        confirmed,
                        Set.of()));
    }

    private Fact fact(String code, String value) {
        return new Fact(code, code, new BigDecimal(value), "%", value, "https://example.com", false);
    }
}
