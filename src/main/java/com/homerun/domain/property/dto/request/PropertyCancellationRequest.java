package com.homerun.domain.property.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 매물 해제 입력(FR-P8-06). */
@Schema(description = "매물 해제(FR-P8-06)")
public record PropertyCancellationRequest(@NotBlank String reason) {}
