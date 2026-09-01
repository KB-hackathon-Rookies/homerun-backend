package com.homerun.global.external.building;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("external-api.building-register")
public record BuildingRegisterProperties(String baseUrl, String serviceKey, Endpoints endpoints) {

    public record Endpoints(String title, String housingPrice) {}
}
