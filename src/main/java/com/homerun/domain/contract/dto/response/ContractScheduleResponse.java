package com.homerun.domain.contract.dto.response;

import com.homerun.domain.contract.type.ApplicationMethod;
import com.homerun.domain.contract.type.ContractCollateralMethod;
import com.homerun.domain.contract.type.LoanProductKind;
import com.homerun.domain.plan.type.HouseType;
import java.time.LocalDate;
import java.util.List;

public record ContractScheduleResponse(
        LocalDate balanceDate,
        LocalDate applicationDeadline,
        LoanProductKind loanProductKind,
        ContractCollateralMethod collateralMethod,
        ApplicationMethod applicationMethod,
        HouseType houseType,
        boolean compressedSchedule,
        List<Milestone> milestones,
        List<String> warnings) {

    public ContractScheduleResponse {
        milestones = List.copyOf(milestones);
        warnings = List.copyOf(warnings);
    }

    public record Milestone(String code, String label, LocalDate dueDate, String reason, boolean blocking) {}
}
