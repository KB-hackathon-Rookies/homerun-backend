package com.homerun.domain.diagnosis.dto.response;

import com.homerun.domain.diagnosis.type.FirstBaseCompletionStatus;
import com.homerun.domain.plan.dto.response.PlanProgressResponse;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import com.homerun.domain.policy.dto.response.JeonsePolicyVerdictListResponse;
import java.util.List;

public record FirstBaseCompleteResponse(
        FirstBaseCompletionStatus status,
        boolean replayed,
        int inputRevision,
        DiagnosisResponse diagnosis,
        JeonsePolicyVerdictListResponse policies,
        List<FirstBaseLoanScenarioResponse> loanScenarios,
        List<PlanInputUnknownField> confirmationRequiredFields,
        PlanProgressResponse progress,
        java.time.Instant completedAt) {

    public FirstBaseCompleteResponse {
        loanScenarios = List.copyOf(loanScenarios);
        confirmationRequiredFields = List.copyOf(confirmationRequiredFields);
    }
}
