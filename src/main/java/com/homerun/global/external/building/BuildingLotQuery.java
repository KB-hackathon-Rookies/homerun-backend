package com.homerun.global.external.building;

public record BuildingLotQuery(String legalDistrictCode, boolean mountain, String mainLotNumber, String subLotNumber) {

    public String sigunguCode() {
        return legalDistrictCode.substring(0, 5);
    }

    public String bjdongCode() {
        return legalDistrictCode.substring(5, 10);
    }

    public String platGbCode() {
        return mountain ? "1" : "0";
    }

    public String paddedMainLotNumber() {
        return padLotNumber(mainLotNumber);
    }

    public String paddedSubLotNumber() {
        return padLotNumber(subLotNumber);
    }

    private String padLotNumber(String value) {
        String normalized = value == null || value.isBlank() ? "0" : value;
        return String.format("%04d", Integer.parseInt(normalized));
    }
}
