package com.homerun.domain.property.dto.response;

import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.type.TrafficLight;
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

        @Schema(description = "2루 매물 카드 상태(FR-P1-02). RED면 대출 상품을 노출하지 않는다")
        TrafficLight trafficLight,

        @Schema(description = "신호등의 한글 이름. 색만으로 구분하지 않는다(NFR-UX-03)")
        String trafficLightLabel,

        Instant analyzedAt) {

    public static PropertyCandidateResponse from(Property property, TrafficLight trafficLight) {
        return new PropertyCandidateResponse(
                property.getId(),
                property.getAddress(),
                property.getRoadAddress(),
                property.getBuildingName(),
                property.getHouseType(),
                property.getDeposit(),
                property.isSelected(),
                trafficLight,
                trafficLight == null ? null : trafficLight.label(),
                property.getAnalyzedAt());
    }
}
