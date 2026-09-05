package com.homerun.domain.plan.dto.response;

import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.FinancialValueSource;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.HouseholderStatus;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PlanInputResponse(
        Long planId,
        Long hopeDeposit,
        Long currentDeposit,
        Long monthlyRent,
        Long maintenanceFee,
        Long maxMonthlyBurden,
        Long regionId,
        BigDecimal areaM2,
        HouseType houseType,
        Boolean isHomeless,
        HouseholderStatus householderStatus,
        MaritalStatus maritalStatus,
        EmploymentType employmentType,
        Integer employmentMonths,
        CompanySize companySize,
        Boolean householdHomeless,
        Boolean livesApartFromParents,
        Boolean parentOnHousingBenefit,
        LocalDate birthDate,
        Integer militaryMonths,
        Long monthlyIncome,
        Long netAssets,
        Long availableCash,
        Boolean existingJeonseLoan,
        FinancialValueSource incomeSource,
        FinancialValueSource assetSource,
        Boolean financialDataConfirmed,
        List<PlanInputUnknownField> unknownFields,
        int revision,
        Instant savedAt,
        String saveStatus) {

    public static PlanInputResponse from(PlanInput input) {
        return new PlanInputResponse(
                input.getPlanId(),
                input.getHopeDeposit(),
                input.getCurrentDeposit(),
                input.getMonthlyRent(),
                input.getMaintenanceFee(),
                input.getMaxMonthlyBurden(),
                input.getRegionId(),
                input.getAreaM2(),
                input.getHouseType(),
                input.getHomeless(),
                input.getHouseholderStatus(),
                input.getMaritalStatus(),
                input.getEmploymentType(),
                input.getEmploymentMonths(),
                input.getCompanySize(),
                input.getHouseholdHomeless(),
                input.getLivesApartFromParents(),
                input.getParentOnHousingBenefit(),
                input.getBirthDate(),
                input.getMilitaryMonths(),
                input.getMonthlyIncome(),
                input.getNetAssets(),
                input.getAvailableCash(),
                input.getExistingJeonseLoan(),
                input.getIncomeSource(),
                input.getAssetSource(),
                input.getFinancialDataConfirmed(),
                input.getUnknownFields(),
                input.getRevision(),
                input.getUpdatedAt(),
                "SAVED");
    }
}
