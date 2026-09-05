package com.homerun.domain.plan.validation;

import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.PlanGate;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.exception.FieldValidationException;
import com.homerun.global.response.FieldErrorDetail;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class PlanInputCompletionValidator {

    private static final String REQUIRED_MESSAGE = "값을 입력하거나 모름으로 표시해 주세요.";

    private static final List<InputRequirement> LEGACY_DIAGNOSIS_REQUIREMENTS = List.of(
            requirement(PlanInputUnknownField.HOPE_DEPOSIT, "hopeDeposit", PlanInput::getHopeDeposit),
            requirement(PlanInputUnknownField.CURRENT_DEPOSIT, "currentDeposit", PlanInput::getCurrentDeposit),
            requirement(PlanInputUnknownField.MONTHLY_RENT, "monthlyRent", PlanInput::getMonthlyRent),
            requirement(PlanInputUnknownField.MAINTENANCE_FEE, "maintenanceFee", PlanInput::getMaintenanceFee),
            requirement(PlanInputUnknownField.MAX_MONTHLY_BURDEN, "maxMonthlyBurden", PlanInput::getMaxMonthlyBurden),
            requirement(PlanInputUnknownField.REGION_ID, "regionId", PlanInput::getRegionId),
            requirement(PlanInputUnknownField.AREA_M2, "areaM2", PlanInput::getAreaM2),
            requirement(PlanInputUnknownField.HOUSE_TYPE, "houseType", PlanInput::getHouseType),
            requirement(PlanInputUnknownField.IS_HOMELESS, "isHomeless", PlanInput::getHomeless),
            requirement(PlanInputUnknownField.HOUSEHOLDER_STATUS, "householderStatus", PlanInput::getHouseholderStatus),
            requirement(PlanInputUnknownField.MARITAL_STATUS, "maritalStatus", PlanInput::getMaritalStatus),
            requirement(PlanInputUnknownField.EMPLOYMENT_TYPE, "employmentType", PlanInput::getEmploymentType),
            requirement(PlanInputUnknownField.EMPLOYMENT_MONTHS, "employmentMonths", PlanInput::getEmploymentMonths),
            requirement(PlanInputUnknownField.COMPANY_SIZE, "companySize", PlanInput::getCompanySize));

    private static final List<InputRequirement> JEONSE_DIAGNOSIS_REQUIREMENTS = List.of(
            requirement(PlanInputUnknownField.HOPE_DEPOSIT, "hopeDeposit", PlanInput::getHopeDeposit),
            requirement(PlanInputUnknownField.REGION_ID, "regionId", PlanInput::getRegionId),
            requirement(PlanInputUnknownField.IS_HOMELESS, "isHomeless", PlanInput::getHomeless),
            requirement(PlanInputUnknownField.HOUSEHOLDER_STATUS, "householderStatus", PlanInput::getHouseholderStatus),
            requirement(PlanInputUnknownField.EMPLOYMENT_TYPE, "employmentType", PlanInput::getEmploymentType),
            requirement(PlanInputUnknownField.MONTHLY_INCOME, "monthlyIncome", PlanInput::getMonthlyIncome),
            requirement(PlanInputUnknownField.NET_ASSETS, "netAssets", PlanInput::getNetAssets),
            requirement(PlanInputUnknownField.AVAILABLE_CASH, "availableCash", PlanInput::getAvailableCash));

    private final PlanInputRepository inputRepository;
    private final PlanRepository planRepository;

    public PlanInputCompletionValidator(PlanInputRepository inputRepository, PlanRepository planRepository) {
        this.inputRepository = inputRepository;
        this.planRepository = planRepository;
    }

    public void validate(Long planId, PlanGate gate) {
        if (gate != PlanGate.FIRST_DIAGNOSIS) {
            return;
        }

        PlanInput input = inputRepository.findByPlanId(planId).orElse(null);
        LeaseType leaseType = planRepository
                .findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND))
                .getLeaseType();
        Set<PlanInputUnknownField> unknownFields = input == null ? Set.of() : Set.copyOf(input.getUnknownFields());
        List<FieldErrorDetail> fieldErrors = requirements(leaseType, input).stream()
                .filter(requirement -> requirement.isMissing(input, unknownFields))
                .map(requirement -> new FieldErrorDetail(requirement.fieldName(), REQUIRED_MESSAGE))
                .toList();
        if (!fieldErrors.isEmpty()) {
            throw new FieldValidationException(ErrorCode.PLAN_REQUIRED_INPUT_MISSING, fieldErrors);
        }
    }

    private List<InputRequirement> requirements(LeaseType leaseType, PlanInput input) {
        if (leaseType != LeaseType.JEONSE) {
            return LEGACY_DIAGNOSIS_REQUIREMENTS;
        }
        List<InputRequirement> requirements = new ArrayList<>(JEONSE_DIAGNOSIS_REQUIREMENTS);
        EmploymentType employment = input == null ? null : input.getEmploymentType();
        if (employment == EmploymentType.FULL_TIME
                || employment == EmploymentType.CONTRACT
                || employment == EmploymentType.DAILY_WORKER
                || employment == EmploymentType.INTERN) {
            requirements.add(requirement(
                    PlanInputUnknownField.EMPLOYMENT_MONTHS, "employmentMonths", PlanInput::getEmploymentMonths));
            requirements.add(requirement(PlanInputUnknownField.COMPANY_SIZE, "companySize", PlanInput::getCompanySize));
        }
        return requirements;
    }

    private static InputRequirement requirement(
            PlanInputUnknownField unknownField, String fieldName, Function<PlanInput, Object> valueReader) {
        return new InputRequirement(unknownField, fieldName, true, valueReader);
    }

    private record InputRequirement(
            PlanInputUnknownField unknownField,
            String fieldName,
            boolean unknownAllowed,
            Function<PlanInput, Object> valueReader) {

        private boolean isMissing(PlanInput input, Set<PlanInputUnknownField> unknownFields) {
            if (input != null && valueReader.apply(input) != null) {
                return false;
            }
            return !unknownAllowed || !unknownFields.contains(unknownField);
        }
    }
}
