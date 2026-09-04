package com.homerun.domain.region.dto.response;

import com.homerun.domain.region.type.PolicyArea;

public record RegionOptionResponse(Long id, String code, String name, PolicyArea policyArea) {}
