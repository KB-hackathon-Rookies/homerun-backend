package com.homerun.domain.property.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "매물 비교 화면의 은행 상담 요약")
public record PropertyConsultationSummaryResponse(
        Long propertyId,
        int consultationCount,
        BankConsultationResponse latestConsultation,
        BankConsultationResponse bestPossibleConsultation) {}
