package com.homerun.domain.property.dto.response;

import com.homerun.domain.property.entity.PropertyDecision;
import java.time.Instant;

public record PropertyDecisionResponse(
        Long decisionId,
        int decisionRevision,
        PropertyCandidateResponse property,
        BankConsultationResponse consultation,
        Instant decidedAt) {

    public static PropertyDecisionResponse from(
            PropertyDecision decision, PropertyCandidateResponse property, BankConsultationResponse consultation) {
        return new PropertyDecisionResponse(
                decision.getId(), decision.getRevision(), property, consultation, decision.getDecidedAt());
    }
}
