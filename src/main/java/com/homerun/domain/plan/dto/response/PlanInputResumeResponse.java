package com.homerun.domain.plan.dto.response;

import com.homerun.domain.plan.type.DiagnosisInputStep;
import java.util.List;

public record PlanInputResumeResponse(
        DiagnosisInputStep resumeStep,
        List<DiagnosisInputStep> completedSteps,
        List<DiagnosisInputStep> skippedSteps,
        int progressPercent,
        int revision,
        PlanInputResponse input) {

    public PlanInputResumeResponse {
        completedSteps = List.copyOf(completedSteps);
        skippedSteps = List.copyOf(skippedSteps);
    }
}
