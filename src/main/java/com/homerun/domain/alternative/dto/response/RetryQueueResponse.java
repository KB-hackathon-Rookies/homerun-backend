package com.homerun.domain.alternative.dto.response;

import java.util.List;

public record RetryQueueResponse(Long planId, List<RetryQueueItemResponse> items) {}
