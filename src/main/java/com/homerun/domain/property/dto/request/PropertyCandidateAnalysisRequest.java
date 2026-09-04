package com.homerun.domain.property.dto.request;

import com.homerun.domain.house.dto.request.HouseAnalysisRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "매물 후보 통합조회 및 안전성 판정 요청")
public record PropertyCandidateAnalysisRequest(
        @Valid @NotNull HouseAnalysisRequest house,
        @NotNull @PositiveOrZero Long deposit,
        @PositiveOrZero Long marketPrice,
        @PositiveOrZero Long officialPrice,
        @PositiveOrZero Long seniorDebt,
        Boolean ownerMatches,
        Boolean trustRegistered,
        Boolean landlordTaxUnpaid) {}
