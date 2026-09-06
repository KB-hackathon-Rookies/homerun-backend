package com.homerun.domain.settlement.dto.response;

import com.homerun.domain.settlement.type.DashboardItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "정착 항목 한 개")
public record DashboardItemResponse(String code, String label, DashboardItemStatus status) {}
