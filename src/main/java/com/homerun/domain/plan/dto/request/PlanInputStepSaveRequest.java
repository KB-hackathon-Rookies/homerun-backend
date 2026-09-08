package com.homerun.domain.plan.dto.request;

import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.FinancialValueSource;
import com.homerun.domain.plan.type.HouseholderStatus;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Set;

@Schema(description = "1루 진단 STEP 부분 저장. stepCode에 해당하는 필드만 전송합니다.")
public record PlanInputStepSaveRequest(
        @Min(0) @Schema(description = "현재 plan_input revision. 최초 저장은 0", example = "2")
        int expectedRevision,

        HouseholderStatus householderStatus,
        Boolean isHomeless,
        Boolean householdHomeless,
        MaritalStatus maritalStatus,
        EmploymentType employmentType,
        CompanySize companySize,
        @PositiveOrZero Integer employmentMonths,
        @PositiveOrZero Long monthlyIncome,
        @PositiveOrZero Long netAssets,
        @PositiveOrZero Long availableCash,
        Boolean existingJeonseLoan,

        @Schema(
                description =
                        "세대원 기금대출과 배우자의 전세·주택담보대출이 없음을 확인했는가(FCT-258). " + "사용자 진술이며 은행 검증이 아니다. 보내지 않아도 STEP 은 완료된다")
        Boolean prohibitedLoanConfirmed,

        FinancialValueSource incomeSource,
        FinancialValueSource assetSource,
        Boolean financialDataConfirmed,
        @PositiveOrZero Long hopeDeposit,
        @Positive Long regionId,
        Set<PlanInputUnknownField> unknownFields) {

    public Set<PlanInputUnknownField> normalizedUnknownFields() {
        return unknownFields == null ? Set.of() : Set.copyOf(unknownFields);
    }

    public Object valueOf(PlanInputUnknownField field) {
        return switch (field) {
            case HOUSEHOLDER_STATUS -> householderStatus;
            case IS_HOMELESS -> isHomeless;
            case HOUSEHOLD_HOMELESS -> householdHomeless;
            case MARITAL_STATUS -> maritalStatus;
            case EMPLOYMENT_TYPE -> employmentType;
            case COMPANY_SIZE -> companySize;
            case EMPLOYMENT_MONTHS -> employmentMonths;
            case MONTHLY_INCOME -> monthlyIncome;
            case NET_ASSETS -> netAssets;
            case AVAILABLE_CASH -> availableCash;
            case EXISTING_JEONSE_LOAN -> existingJeonseLoan;
            case PROHIBITED_LOAN_CONFIRMED -> prohibitedLoanConfirmed;
            case INCOME_SOURCE -> incomeSource;
            case ASSET_SOURCE -> assetSource;
            case FINANCIAL_DATA_CONFIRMED -> financialDataConfirmed;
            case HOPE_DEPOSIT -> hopeDeposit;
            case REGION_ID -> regionId;
            default -> null;
        };
    }
}
