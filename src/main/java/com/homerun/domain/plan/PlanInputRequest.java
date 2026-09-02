package com.homerun.domain.plan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
        @Size(max = 30) String houseType,
        Boolean isHomeless,
        @Size(max = 30) String householderStatus,
        @Size(max = 20) String maritalStatus,
        @Size(max = 30) String employmentType,
        @PositiveOrZero Integer employmentMonths,
        @Size(max = 30) String companySize,
        Set<String> unknownFields) {

    public Set<String> normalizedUnknownFields() {
        return unknownFields == null ? Set.of() : Set.copyOf(unknownFields);
    }
}
