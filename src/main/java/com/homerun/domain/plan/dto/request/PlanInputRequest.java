package com.homerun.domain.plan.dto.request;

import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.FinancialValueSource;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.HouseholderStatus;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record PlanInputRequest(
        @PositiveOrZero(message = "희망 보증금은 0원 이상이어야 합니다") Long hopeDeposit,
        @PositiveOrZero(message = "현재 보유자금은 0원 이상이어야 합니다") Long currentDeposit,
        @PositiveOrZero(message = "월세는 0원 이상이어야 합니다") Long monthlyRent,
        @PositiveOrZero(message = "관리비는 0원 이상이어야 합니다") Long maintenanceFee,
        @PositiveOrZero(message = "월 부담 상한은 0원 이상이어야 합니다") Long maxMonthlyBurden,
        @Positive(message = "지역 식별자는 1 이상이어야 합니다") Long regionId,

        @DecimalMin(value = "0.01", message = "주택 면적은 0보다 커야 합니다")
        BigDecimal areaM2,

        HouseType houseType,
        Boolean isHomeless,
        HouseholderStatus householderStatus,
        MaritalStatus maritalStatus,
        EmploymentType employmentType,
        @PositiveOrZero(message = "재직기간은 0개월 이상이어야 합니다") Integer employmentMonths,
        CompanySize companySize,
        Boolean householdHomeless,
        LocalDate birthDate,
        @PositiveOrZero @Max(60) Integer militaryMonths,
        @PositiveOrZero Long monthlyIncome,
        @PositiveOrZero Long netAssets,
        @PositiveOrZero Long availableCash,
        Boolean existingJeonseLoan,
        FinancialValueSource incomeSource,
        FinancialValueSource assetSource,
        Boolean financialDataConfirmed,
        Set<PlanInputUnknownField> unknownFields) {

    public Set<PlanInputUnknownField> normalizedUnknownFields() {
        return unknownFields == null ? Set.of() : Set.copyOf(unknownFields);
    }
}
