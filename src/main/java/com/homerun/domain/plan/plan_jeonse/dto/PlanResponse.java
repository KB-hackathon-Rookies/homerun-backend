package com.homerun.domain.plan.plan_jeonse.dto;

import com.homerun.domain.plan.enums.*;

import com.homerun.domain.plan.plan_jeonse.domain.Plan;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class PlanResponse {

    private Long id;

    private Long userId;

    private LeaseType leaseType;

    private String startSituation;

    private PlanStage stage;

    private PlanStatus status;

    private Boolean isFavorite;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime closedAt;

    public static PlanResponse from(Plan plan) {

        return PlanResponse.builder()
                .id(plan.getId())
                .userId(plan.getUserId())
                .leaseType(plan.getLeaseType())
                .startSituation(plan.getStartSituation())
                .stage(plan.getStage())
                .status(plan.getStatus())
                .isFavorite(plan.getIsFavorite())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .closedAt(plan.getClosedAt())
                .build();
    }
}