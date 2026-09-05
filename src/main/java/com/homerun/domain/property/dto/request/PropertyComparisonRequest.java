package com.homerun.domain.property.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "나란히 비교할 매물 2~3개")
public record PropertyComparisonRequest(
        @NotNull @Size(min = 2, max = 3, message = "비교할 매물은 2개 이상 3개 이하여야 합니다")
        List<Long> propertyIds) {

    public PropertyComparisonRequest {
        if (propertyIds != null) {
            propertyIds = List.copyOf(propertyIds);
        }
    }
}
