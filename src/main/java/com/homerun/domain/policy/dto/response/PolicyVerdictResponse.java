package com.homerun.domain.policy.dto.response;

import com.homerun.domain.policy.type.PolicyVerdictResult;
import java.util.List;

public record PolicyVerdictResponse(
        String policyCode, String policyName, PolicyVerdictResult verdict, List<ConditionBasisResponse> basis) {}
