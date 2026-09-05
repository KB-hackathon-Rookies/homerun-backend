package com.homerun.domain.property.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "요청한 순서대로 정렬된 매물 비교 결과")
public record PropertyComparisonResponse(List<PropertyCandidateResponse> properties) {

    public PropertyComparisonResponse {
        properties = List.copyOf(properties);
    }
}
