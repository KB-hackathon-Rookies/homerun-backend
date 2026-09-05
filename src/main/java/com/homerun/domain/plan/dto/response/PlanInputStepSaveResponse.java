package com.homerun.domain.plan.dto.response;

import com.homerun.domain.plan.type.DiagnosisInputStep;
import java.time.Instant;
import java.util.List;

public record PlanInputStepSaveResponse(
        DiagnosisInputStep savedStep,
        DiagnosisInputStep nextStep,
        List<DiagnosisInputStep> completedSteps,
        List<DiagnosisInputStep> skippedSteps,
        int progressPercent,
        int revision,
        Instant savedAt,
        PlanInputResponse input) {

    public PlanInputStepSaveResponse {
        completedSteps = List.copyOf(completedSteps);
        skippedSteps = List.copyOf(skippedSteps);
    }
}
