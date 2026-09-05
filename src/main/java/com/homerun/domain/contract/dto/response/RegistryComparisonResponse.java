package com.homerun.domain.contract.dto.response;

import com.homerun.domain.contract.type.RegistryComparisonStatus;
import java.time.LocalDate;
import java.util.List;

public record RegistryComparisonResponse(
        RegistryComparisonStatus status,
        boolean stopPayment,
        LocalDate signingRegistryIssuedAt,
        LocalDate settlementRegistryIssuedAt,
        List<String> changedRisks,
        String action) {

    public RegistryComparisonResponse {
        changedRisks = List.copyOf(changedRisks);
    }
}
