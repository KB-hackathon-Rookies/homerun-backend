package com.homerun.domain.diagnosis.dto.response;

import com.homerun.domain.plan.dto.response.PlanProgressResponse;
import com.homerun.domain.policy.dto.response.JeonsePolicyVerdictListResponse;
import java.time.Instant;
import java.util.List;

public record FirstBaseResultResponse(
        int inputRevision,
        DiagnosisResponse diagnosis,
        JeonsePolicyVerdictListResponse policies,
        List<FirstBaseLoanScenarioResponse> loanScenarios,
        Instant completedAt,
        PlanProgressResponse progress) {

    public FirstBaseResultResponse {
        loanScenarios = List.copyOf(loanScenarios);
    }

    public static FirstBaseResultResponse from(FirstBaseResultSnapshot snapshot, PlanProgressResponse progress) {
        return new FirstBaseResultResponse(
                snapshot.inputRevision(),
                snapshot.diagnosis(),
                snapshot.policies(),
                snapshot.loanScenarios(),
                snapshot.completedAt(),
                progress);
    }
}
