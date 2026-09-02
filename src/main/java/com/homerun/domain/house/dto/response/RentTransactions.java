package com.homerun.domain.house.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "지역 전체 전월세 실거래가 중 선택한 집과 일치하는 거래")
public record RentTransactions(
        boolean available,
        int sourceTotalCount,
        int matchedCount,
        String errorMessage,
        List<Map<String, String>> items) {}
