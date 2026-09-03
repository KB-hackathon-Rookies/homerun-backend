package com.homerun.domain.openbanking.dto.response;

public record ExternalDataCoverage(int requested, int succeeded) {

    public boolean complete() {
        return requested == succeeded;
    }
}
