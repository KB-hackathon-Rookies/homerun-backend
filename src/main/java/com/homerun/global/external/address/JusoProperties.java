package com.homerun.global.external.address;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("external-api.juso")
public record JusoProperties(String baseUrl, String confirmKey) {}
