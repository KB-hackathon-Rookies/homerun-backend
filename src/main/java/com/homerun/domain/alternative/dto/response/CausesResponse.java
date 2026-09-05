package com.homerun.domain.alternative.dto.response;

import java.util.List;

public record CausesResponse(Long planId, List<CauseResponse> causes) {}
