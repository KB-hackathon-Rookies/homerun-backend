package com.homerun.domain.policy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 부대비용·총비용 시뮬레이션 결과(BR-08a·BR-21).
 *
 * @param deposit 계획의 보증금(원)
 * @param loanAmount 대출금(원)
 * @param ancillary 부대비용 내역(BR-08a)
 * @param totalCostYear1 1년차 총비용(BR-21)
 */
@Schema(description = "부대비용·총비용 시뮬레이션 결과")
public record CostSimulationResponse(
        long deposit, long loanAmount, AncillaryCostResponse ancillary, TotalCostResponse totalCostYear1) {}
