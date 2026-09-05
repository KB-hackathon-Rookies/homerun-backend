package com.homerun.domain.property.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 매물 하나의 상품별 판정 전체.
 *
 * @param evaluatedAt 이번에 판정한 시각. 저장된 것을 읽기만 했으면 null
 */
@Schema(description = "매물 하나에 대한 상품별 판정")
public record PropertyPolicyVerdictListResponse(
        Long propertyId, List<PropertyPolicyVerdictResponse> results, Instant evaluatedAt) {

    public PropertyPolicyVerdictListResponse {
        results = List.copyOf(results);
    }
}
