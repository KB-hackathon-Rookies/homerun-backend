package com.homerun.domain.property.dto.response;

import com.homerun.domain.property.entity.Property;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "전세 계획의 매물 후보")
public record PropertyCandidateResponse(
        Long propertyId,
        String address,
        String roadAddress,
        String buildingName,
        String houseType,
        Long deposit,
        boolean selected,
        Instant analyzedAt) {

    public static PropertyCandidateResponse from(Property property) {
        return new PropertyCandidateResponse(
                property.getId(),
                property.getAddress(),
                property.getRoadAddress(),
                property.getBuildingName(),
                property.getHouseType(),
                property.getDeposit(),
                property.isSelected(),
                property.getAnalyzedAt());
    }
}
