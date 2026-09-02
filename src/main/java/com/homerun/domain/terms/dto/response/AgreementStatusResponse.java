package com.homerun.domain.terms.dto.response;

import java.time.Instant;
import java.util.List;

public record AgreementStatusResponse(boolean allRequiredAgreed, List<Item> agreements) {

    public record Item(String code, String version, boolean required, boolean agreed, Instant agreedAt) {}
}
