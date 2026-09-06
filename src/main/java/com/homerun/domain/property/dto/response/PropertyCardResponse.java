package com.homerun.domain.property.dto.response;

import com.homerun.domain.property.type.OfficialPriceSource;
import java.util.List;

public record PropertyCardResponse(
        PropertyCandidateResponse property,
        PropertyWorkflowResponse workflow,
        BuildingSafetyFactsResponse buildingSafety,
        Long officialPrice,
        Integer officialPriceYear,
        OfficialPriceSource officialPriceSource,
        List<PropertyCheckResponse> checks,
        PropertyPolicyVerdictListResponse loanProducts,
        List<BankConsultationResponse> consultations) {

    public PropertyCardResponse {
        checks = List.copyOf(checks);
        consultations = List.copyOf(consultations);
    }
}
