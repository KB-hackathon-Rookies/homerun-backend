package com.homerun.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 현금흐름 종합(FR-H5-03). 저장된 대출·고정지출·소득·생활비로 월간 지표를 계산한다.
 *
 * @param loanPrincipal 대출 원금(원, loan_account)
 * @param monthlyIncome 월 소득(원, plan_input). 없으면 0
 * @param managementFee 월 관리비(원, fixed_expense 의 MGMT 합계)
 * @param livingCost 월 생활비(원, 최근 진단). 없으면 0
 * @param metrics 월 이자·주거비·잔여금(BR-28)
 */
@Schema(description = "현금흐름 종합(FR-H5-03)")
public record CashFlowSummaryResponse(
        long loanPrincipal, long monthlyIncome, long managementFee, long livingCost, MonthlyMetricsResponse metrics) {}
