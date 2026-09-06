package com.homerun.domain.property.dto.request;

import com.homerun.domain.plan.type.HouseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "매물 STEP 2 수동 입력")
public record PropertyBuildingStepRequest(
        @Min(1) int expectedRevision,
        @NotNull HouseType houseType,
        @NotNull @Positive BigDecimal exclusiveArea) {}
