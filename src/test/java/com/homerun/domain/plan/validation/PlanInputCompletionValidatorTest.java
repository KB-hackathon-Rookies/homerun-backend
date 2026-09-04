package com.homerun.domain.plan.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.HouseholderStatus;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.domain.plan.type.PlanGate;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.exception.FieldValidationException;
import com.homerun.global.response.FieldErrorDetail;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanInputCompletionValidatorTest {

    private static final Long PLAN_ID = 10L;

    @Mock
    private PlanInputRepository inputRepository;

    private PlanInputCompletionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PlanInputCompletionValidator(inputRepository);
    }

    @Test
    void should_acceptDiagnosisCompletion_when_allRequiredValuesExist() {
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(PlanInput.create(PLAN_ID, completeInput())));

        assertThatCode(() -> validator.validate(PLAN_ID, PlanGate.FIRST_DIAGNOSIS))
                .doesNotThrowAnyException();
    }

    @Test
    void should_acceptUnknownFieldButReportOtherMissingFields() {
        PlanInputRequest request = new PlanInputRequest(
                100_000_000L,
                20_000_000L,
                null,
                null,
                800_000L,
                1L,
                new BigDecimal("33.25"),
                HouseType.APARTMENT,
                true,
                HouseholderStatus.CURRENT,
                MaritalStatus.SINGLE,
                EmploymentType.FULL_TIME,
                12,
                CompanySize.SMALL,
                Set.of(PlanInputUnknownField.MONTHLY_RENT));
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(PlanInput.create(PLAN_ID, request)));

        assertThatThrownBy(() -> validator.validate(PLAN_ID, PlanGate.FIRST_DIAGNOSIS))
                .isInstanceOfSatisfying(FieldValidationException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_REQUIRED_INPUT_MISSING);
                    assertThat(exception.fieldErrors())
                            .extracting(FieldErrorDetail::field)
                            .containsExactly("maintenanceFee");
                });
    }

    @Test
    void should_reportAllRequiredFields_when_inputSnapshotDoesNotExist() {
        when(inputRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(PLAN_ID, PlanGate.FIRST_DIAGNOSIS))
                .isInstanceOfSatisfying(
                        FieldValidationException.class,
                        exception -> assertThat(exception.fieldErrors())
                                .hasSize(14)
                                .extracting(FieldErrorDetail::field)
                                .contains("hopeDeposit", "regionId", "employmentType", "companySize"));
    }

    @Test
    void should_notReadPlanInput_when_gateHasNoInputRequirement() {
        assertThatCode(() -> validator.validate(PLAN_ID, PlanGate.SECOND_POLICY_SELECTION))
                .doesNotThrowAnyException();

        verifyNoInteractions(inputRepository);
    }

    private PlanInputRequest completeInput() {
        return new PlanInputRequest(
                100_000_000L,
                20_000_000L,
                500_000L,
                100_000L,
                800_000L,
                1L,
                new BigDecimal("33.25"),
                HouseType.APARTMENT,
                true,
                HouseholderStatus.CURRENT,
                MaritalStatus.SINGLE,
                EmploymentType.FULL_TIME,
                12,
                CompanySize.SMALL,
                Set.of());
    }
}
