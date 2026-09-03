package com.homerun.domain.plan.dto.request;

import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.HouseholderStatus;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Set;

public record PlanInputRequest(
        @PositiveOrZero Long hopeDeposit,
        @PositiveOrZero Long currentDeposit,
        @PositiveOrZero Long monthlyRent,
        @PositiveOrZero Long maintenanceFee,
        @PositiveOrZero Long maxMonthlyBurden,
        @Positive Long regionId,
        @DecimalMin("0.01") BigDecimal areaM2,
        HouseType houseType,
        Boolean isHomeless,
        HouseholderStatus householderStatus,
        MaritalStatus maritalStatus,
        EmploymentType employmentType,
        @PositiveOrZero Integer employmentMonths,
        CompanySize companySize,
        Set<PlanInputUnknownField> unknownFields) {

    public Set<PlanInputUnknownField> normalizedUnknownFields() {
        return unknownFields == null ? Set.of() : Set.copyOf(unknownFields);
    }
}
