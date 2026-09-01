package com.homerun.global.external.realestate;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("external-api.real-estate-transaction")
public record RealEstateTransactionProperties(String baseUrl, String serviceKey, Endpoints endpoints) {

    public String endpointFor(HousingType housingType) {
        return switch (housingType) {
            case APARTMENT -> endpoints.apartment();
            case OFFICETEL -> endpoints.officetel();
            case ROW_HOUSE -> endpoints.rowHouse();
        };
    }

    public record Endpoints(String apartment, String officetel, String rowHouse) {}
}
