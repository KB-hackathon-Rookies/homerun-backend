package com.homerun.domain.house.dto.response;

import com.homerun.domain.house.dto.request.HouseAnalysisRequest;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.global.external.building.BuildingLedgerResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "주소·건축물대장·실거래가 통합 조회 결과")
public record HouseAnalysisResponse(
        HouseAnalysisRequest selectedAddress,
        HouseType resolvedHouseType,
        BuildingLedgerResponse buildingLedger,
        RentTransactions rents,
        List<String> warnings) {}
