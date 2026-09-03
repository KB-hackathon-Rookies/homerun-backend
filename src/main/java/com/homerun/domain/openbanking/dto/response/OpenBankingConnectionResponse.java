package com.homerun.domain.openbanking.dto.response;

import com.homerun.domain.openbanking.entity.OpenBankingConnection;
import java.time.Instant;

public record OpenBankingConnectionResponse(
        boolean connected, String scope, Instant accessTokenExpiresAt, Instant lastConnectedAt) {

    public static OpenBankingConnectionResponse disconnected() {
        return new OpenBankingConnectionResponse(false, null, null, null);
    }

    public static OpenBankingConnectionResponse from(OpenBankingConnection connection) {
        return new OpenBankingConnectionResponse(
                true, connection.getScope(), connection.getAccessTokenExpiresAt(), connection.getUpdatedAt());
    }
}
