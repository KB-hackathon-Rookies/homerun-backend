package com.homerun.domain.plan.dto.response;

import com.homerun.domain.plan.entity.PlanInput;
import java.math.BigDecimal;
import java.time.Instant;
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
        String houseType,
        Boolean isHomeless,
        String householderStatus,
        String maritalStatus,
        String employmentType,
        Integer employmentMonths,
        String companySize,
        List<String> unknownFields,
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
                input.getUnknownFields(),
                input.getRevision(),
                input.getUpdatedAt(),
                "SAVED");
    }
}
