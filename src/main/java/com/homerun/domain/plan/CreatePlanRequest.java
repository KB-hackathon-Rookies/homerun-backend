package com.homerun.domain.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreatePlanRequest(
        @NotNull @Schema(description = "임대차 유형", example = "JEONSE")
        LeaseType leaseType,

        @Schema(description = "목표 이사일", example = "2027-02-01")
        LocalDate targetMoveDate) {}
