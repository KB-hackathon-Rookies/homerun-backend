package com.homerun.global.external.realestate;

public record RealEstateTransactionRequest(
        HousingType housingType, String legalDistrictCode, String dealYearMonth, int pageNo, int numOfRows) {}
