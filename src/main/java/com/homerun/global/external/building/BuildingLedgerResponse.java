package com.homerun.global.external.building;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "한 필지의 건축물 표제부와 주택가격")
public record BuildingLedgerResponse(BuildingRegisterResponse titles, BuildingRegisterResponse housingPrices) {}
