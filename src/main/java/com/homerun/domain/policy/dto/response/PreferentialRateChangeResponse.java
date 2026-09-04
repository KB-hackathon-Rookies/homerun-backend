package com.homerun.domain.policy.dto.response;

import java.util.List;

public record PreferentialRateChangeResponse(Long planId, List<PreferentialRateChange> changes) {}
