package com.homerun.domain.property.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "건축물대장에서 자동 확인한 안전성 사실")
public record BuildingSafetyFactsResponse(Boolean violationBuilding, Boolean multiHousehold) {}
