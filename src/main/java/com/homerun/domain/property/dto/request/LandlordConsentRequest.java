package com.homerun.domain.property.dto.request;

import com.homerun.domain.property.type.LandlordConsent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 임대인 전세대출 협조 여부 저장(FR-P1-07). */
@Schema(description = "임대인 전세대출 협조 여부(FR-P1-07)")
public record LandlordConsentRequest(@NotNull LandlordConsent consent) {}
