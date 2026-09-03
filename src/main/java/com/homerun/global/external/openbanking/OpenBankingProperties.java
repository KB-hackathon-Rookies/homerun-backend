package com.homerun.global.external.openbanking;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("external-api.open-banking")
public record OpenBankingProperties(
        String oauthBaseUrl,
        String apiBaseUrl,
        String clientId,
        String clientSecret,
        String clientUseCode,
        String redirectUri,
        String scope,
        String tokenEncryptionKey) {}
