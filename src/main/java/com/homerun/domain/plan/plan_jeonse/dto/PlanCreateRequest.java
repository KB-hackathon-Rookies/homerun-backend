package com.homerun.domain.plan.plan_J.dto;

import com.homerun.domain.plan.enums.LeaseType;
import lombok.Data;

@Data
public class PlanCreateRequest {

    private Long userId;

    private LeaseType leaseType;

    //처음 독립인지 기존 월세에서 옮기는지. 벤치 화면의 분기
    private String startSituation;
}