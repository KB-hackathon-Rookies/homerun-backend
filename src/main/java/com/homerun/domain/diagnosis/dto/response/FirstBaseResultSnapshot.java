package com.homerun.domain.diagnosis.dto.response;

import com.homerun.domain.policy.dto.response.JeonsePolicyVerdictListResponse;
import java.time.Instant;
import java.util.List;

public record FirstBaseResultSnapshot(
        int inputRevision,
        DiagnosisResponse diagnosis,
        JeonsePolicyVerdictListResponse policies,
        List<FirstBaseLoanScenarioResponse> loanScenarios,
        Instant completedAt) {

    public FirstBaseResultSnapshot {
        loanScenarios = List.copyOf(loanScenarios);
    }
}
