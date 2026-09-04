package com.homerun.domain.asset.dto.response;

import java.util.List;

public record AssetComparisonResponse(Long planId, List<AssetComparisonResult> results) {}
