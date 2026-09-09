package com.homerun.domain.openbanking.dto.request;

import com.homerun.domain.openbanking.type.Persona;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "데모용 가짜 오픈뱅킹 연동 요청")
public record MockConnectRequest(
        @Schema(description = "적재할 자산 페르소나", example = "KIM_FIRST") @NotNull
        Persona persona) {}
