package com.homerun.domain.plan.dto.response;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.type.*;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlanResponse {

    private Long id;

    private Long userId;

    private LeaseType leaseType;

    private StartSituation startSituation;

    private PlanStage stage;

    private PlanStatus status;

    private Boolean isFavorite;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime closedAt;

    private List<PlanStepResponse> steps;

    private String lastStepCode;

    private String lastTaskCode;

    public static PlanResponse from(Plan plan) {

        return PlanResponse.builder()
                .id(plan.getId())
                .userId(plan.getUserId())
                .leaseType(plan.getLeaseType())
                .startSituation(plan.getStartSituation())
                .stage(plan.getStage())
                .status(plan.getStatus())
                .isFavorite(plan.getIsFavorite())
                .lastStepCode(plan.getLastStepCode())
                .lastTaskCode(plan.getLastTaskCode())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .closedAt(plan.getClosedAt())
                .build();
    }

    public static PlanResponse from(Plan plan, List<PlanStepResponse> steps) {

        return PlanResponse.builder()
                .id(plan.getId())
                .userId(plan.getUserId())
                .leaseType(plan.getLeaseType())
                .startSituation(plan.getStartSituation())
                .stage(plan.getStage())
                .status(plan.getStatus())
                .isFavorite(plan.getIsFavorite())
                .lastStepCode(plan.getLastStepCode())
                .lastTaskCode(plan.getLastTaskCode())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .closedAt(plan.getClosedAt())
                .steps(steps)
                .build();
    }
}
