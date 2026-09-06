package com.homerun.domain.property.dto.response;

import java.time.Instant;

public record SecondBaseResultSnapshot(int decisionRevision, PropertyDecisionResponse decision, Instant completedAt) {}
