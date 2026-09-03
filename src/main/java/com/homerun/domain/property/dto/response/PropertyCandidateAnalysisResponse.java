package com.homerun.domain.property.dto.response;

import com.homerun.domain.house.dto.response.HouseAnalysisResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "저장된 매물 후보의 외부 조회와 안전성 종합 결과")
public record PropertyCandidateAnalysisResponse(
        Long propertyId,
        boolean selected,
        HouseAnalysisResponse houseAnalysis,
        BuildingSafetyFactsResponse automaticFacts,
        PropertyVerification verification) {}
