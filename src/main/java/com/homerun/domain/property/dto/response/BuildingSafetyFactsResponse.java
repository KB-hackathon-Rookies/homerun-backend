package com.homerun.domain.property.dto.response;

import com.homerun.domain.property.type.BuildingVerdict;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "건축물대장에서 자동 확인한 안전성 사실")
public record BuildingSafetyFactsResponse(
        Boolean violationBuilding,
        Boolean multiHousehold,

        @Schema(description = "근린생활시설(비주거)인가. 근생은 모든 전세 상품이 불가다(FCT-120)")
        Boolean nonResidential,

        @Schema(description = "건축물대장 자동 판정(BR-10a)") BuildingVerdict buildingVerdict) {

    /** 주택유형까지 넣어 BR-10a 판정을 파생한다. */
    public static BuildingSafetyFactsResponse of(
            Boolean violationBuilding, Boolean multiHousehold, Boolean nonResidential, String houseType) {
        return new BuildingSafetyFactsResponse(
                violationBuilding,
                multiHousehold,
                nonResidential,
                BuildingVerdict.of(violationBuilding, nonResidential, houseType, multiHousehold));
    }
}
