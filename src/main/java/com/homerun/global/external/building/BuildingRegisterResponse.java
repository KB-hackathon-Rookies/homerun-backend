package com.homerun.global.external.building;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "건축물대장 조회 결과")
public record BuildingRegisterResponse(
        String resultCode, String resultMessage, int totalCount, List<Map<String, String>> items) {}
