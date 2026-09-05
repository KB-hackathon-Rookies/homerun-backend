package com.homerun.domain.plan.validation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.*;
import com.homerun.global.exception.FieldValidationException;
import com.homerun.global.response.FieldErrorDetail;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class JeonseInputCompletionTest {
    private final PlanInputRepository inputs = mock(PlanInputRepository.class);
    private final PlanRepository plans = mock(PlanRepository.class);
    private final PlanInput input = mock(PlanInput.class);
    private final PlanInputCompletionValidator validator = new PlanInputCompletionValidator(inputs, plans);

    @BeforeEach
    void setUp() {
        when(plans.findById(10L)).thenReturn(Optional.of(Plan.create(1L, LeaseType.JEONSE, null)));
        when(inputs.findByPlanId(10L)).thenReturn(Optional.of(input));
        when(input.getHopeDeposit()).thenReturn(180_000_000L);
        when(input.getRegionId()).thenReturn(1L);
        when(input.getHomeless()).thenReturn(true);
        when(input.getHouseholderStatus()).thenReturn(HouseholderStatus.EXPECTED);
        when(input.getMonthlyIncome()).thenReturn(2_450_000L);
        when(input.getNetAssets()).thenReturn(32_000_000L);
        when(input.getAvailableCash()).thenReturn(20_000_000L);
        when(input.getUnknownFields()).thenReturn(List.of());
        when(input.getEmploymentMonths()).thenReturn(null);
    }

    @ParameterizedTest
    @EnumSource(
            value = EmploymentType.class,
            names = {"FREELANCER", "UNEMPLOYED"})
    void should_skipCompanyQuestions_when_notSalaried(EmploymentType type) {
        when(input.getEmploymentType()).thenReturn(type);
        assertThatCode(this::validate).doesNotThrowAnyException();
        // 입력 저장/완료와 서비스 지원 여부·대출 자격은 별도다.
        verify(input, never()).getEmploymentMonths();
        verify(input, never()).getCompanySize();
        verify(input, never()).getMaritalStatus();
    }

    @ParameterizedTest
    @EnumSource(
            value = EmploymentType.class,
            names = {"FULL_TIME", "CONTRACT", "INTERN", "DAILY_WORKER"})
    void should_requireCompanyQuestions_when_salaried(EmploymentType type) {
        when(input.getEmploymentType()).thenReturn(type);
        assertThatThrownBy(this::validate)
                .isInstanceOfSatisfying(
                        FieldValidationException.class,
                        ex -> assertThat(ex.fieldErrors())
                                .extracting(FieldErrorDetail::field)
                                .containsExactly("employmentMonths", "companySize"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11, 12, 30})
    void should_notAddPersonaEmploymentException(int months) {
        when(input.getEmploymentType()).thenReturn(EmploymentType.FULL_TIME);
        when(input.getEmploymentMonths()).thenReturn(months);
        when(input.getCompanySize()).thenReturn(CompanySize.SMALL);
        assertThatCode(this::validate).doesNotThrowAnyException();
        verify(input, never()).getMonthlyRent();
        verify(input, never()).getAreaM2();
    }

    @Test
    void should_acceptExplicitUnknown_butRequireOtherFinancialInput() {
        when(input.getEmploymentType()).thenReturn(EmploymentType.FREELANCER);
        when(input.getMonthlyIncome()).thenReturn(null);
        when(input.getNetAssets()).thenReturn(null);
        when(input.getUnknownFields()).thenReturn(List.of(PlanInputUnknownField.MONTHLY_INCOME));
        assertThatThrownBy(this::validate)
                .isInstanceOfSatisfying(
                        FieldValidationException.class,
                        ex -> assertThat(ex.fieldErrors())
                                .extracting(FieldErrorDetail::field)
                                .containsExactly("netAssets"));
    }

    @Test
    void should_reportEightBaseFields_when_snapshotIsMissing() {
        when(inputs.findByPlanId(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(this::validate)
                .isInstanceOfSatisfying(
                        FieldValidationException.class,
                        ex -> assertThat(ex.fieldErrors())
                                .extracting(FieldErrorDetail::field)
                                .containsExactly(
                                        "hopeDeposit",
                                        "regionId",
                                        "isHomeless",
                                        "householderStatus",
                                        "employmentType",
                                        "monthlyIncome",
                                        "netAssets",
                                        "availableCash"));
    }

    @Test
    void should_preserveLegacyRequirements_when_planIsBanjeonse() {
        when(input.getMonthlyRent()).thenReturn(null);
        when(input.getMaintenanceFee()).thenReturn(null);
        when(plans.findById(10L)).thenReturn(Optional.of(Plan.create(1L, LeaseType.BANJEONSE, null)));
        assertThatThrownBy(this::validate)
                .isInstanceOfSatisfying(
                        FieldValidationException.class,
                        ex -> assertThat(ex.fieldErrors())
                                .extracting(FieldErrorDetail::field)
                                .contains("monthlyRent", "maintenanceFee", "areaM2", "maritalStatus"));
    }

    private void validate() {
        validator.validate(10L, PlanGate.FIRST_DIAGNOSIS);
    }
}
