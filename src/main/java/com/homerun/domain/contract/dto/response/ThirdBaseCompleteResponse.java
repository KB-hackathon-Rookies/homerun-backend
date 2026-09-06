package com.homerun.domain.contract.dto.response;

import com.homerun.domain.contract.type.ContractCollateralMethod;
import com.homerun.domain.plan.dto.response.PlanProgressResponse;
import java.util.List;

/** 잔금·전입신고 완료 후 HOME 단계에서 이어갈 일을 함께 돌려준다. */
public record ThirdBaseCompleteResponse(
        boolean completed,
        ContractCollateralMethod collateralMethod,
        List<HomeHandoff> homeHandoffs,
        PlanProgressResponse progress) {

    public ThirdBaseCompleteResponse {
        homeHandoffs = List.copyOf(homeHandoffs);
    }

    public record HomeHandoff(String taskCode, String label, String action) {}
}
