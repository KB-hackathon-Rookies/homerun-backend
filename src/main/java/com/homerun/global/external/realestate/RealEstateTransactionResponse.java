package com.homerun.global.external.realestate;

import java.util.List;
import java.util.Map;

public record RealEstateTransactionResponse(
        HousingType housingType,
        String legalDistrictCode,
        String dealYearMonth,
        int pageNo,
        int numOfRows,
        int totalCount,
        String resultCode,
        String resultMessage,
        List<Map<String, String>> items) {}
