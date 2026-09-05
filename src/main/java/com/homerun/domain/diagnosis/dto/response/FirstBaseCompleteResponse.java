package com.homerun.domain.diagnosis.dto.response;

import com.homerun.domain.diagnosis.type.FirstBaseCompletionStatus;
import com.homerun.domain.plan.dto.response.PlanProgressResponse;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import java.util.List;

public record FirstBaseCompleteResponse(
        FirstBaseCompletionStatus status,
        boolean replayed,
        int inputRevision,
        DiagnosisResponse diagnosis,
        List<PlanInputUnknownField> confirmationRequiredFields,
        PlanProgressResponse progress) {

    public FirstBaseCompleteResponse {
        confirmationRequiredFields = List.copyOf(confirmationRequiredFields);
    }
}
