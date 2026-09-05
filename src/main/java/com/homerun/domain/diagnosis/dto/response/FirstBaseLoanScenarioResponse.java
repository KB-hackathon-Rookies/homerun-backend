package com.homerun.domain.diagnosis.dto.response;

import com.homerun.domain.policy.dto.response.JeonseLoanCardResponse;

public record FirstBaseLoanScenarioResponse(
        JeonseLoanCardResponse loan, DiagnosisResponse minimumRateScenario, DiagnosisResponse maximumRateScenario) {}
