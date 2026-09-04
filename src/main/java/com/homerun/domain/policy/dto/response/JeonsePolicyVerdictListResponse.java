package com.homerun.domain.policy.dto.response;

import java.time.Instant;
import java.util.List;

public record JeonsePolicyVerdictListResponse(Long planId, List<PolicyVerdictResponse> results, Instant evaluatedAt) {}
