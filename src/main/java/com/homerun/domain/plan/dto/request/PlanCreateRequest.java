package com.homerun.domain.plan.dto.request;

import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.StartSituation;
import lombok.Data;

@Data
public class PlanCreateRequest {

    // 처음 독립인지 기존 월세에서 옮기는지. 벤치 화면의 분기
    private StartSituation startSituation;

    private LeaseType leaseType;
}
