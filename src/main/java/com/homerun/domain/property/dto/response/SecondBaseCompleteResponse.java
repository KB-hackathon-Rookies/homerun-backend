package com.homerun.domain.property.dto.response;

import com.homerun.domain.plan.dto.response.PlanProgressResponse;
import java.time.Instant;

public record SecondBaseCompleteResponse(
        boolean replayed,
        int decisionRevision,
        PropertyDecisionResponse decision,
        PlanProgressResponse progress,
        Instant completedAt) {}
