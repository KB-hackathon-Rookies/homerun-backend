package com.homerun.domain.plan.dto.request;

import com.homerun.domain.plan.type.LeaseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreatePlanRequest(
        @NotNull @Schema(description = "임대차 유형", example = "JEONSE")
        LeaseType leaseType,

        @Schema(description = "목표 이사일", example = "2027-02-01")
        LocalDate targetMoveDate) {}
