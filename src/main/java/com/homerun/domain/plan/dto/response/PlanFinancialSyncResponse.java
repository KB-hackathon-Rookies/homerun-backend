package com.homerun.domain.plan.dto.response;

import com.homerun.domain.openbanking.dto.response.ExternalDataCoverage;
import com.homerun.domain.openbanking.dto.response.FinancialSummaryStatus;
import com.homerun.domain.openbanking.dto.response.OpenBankingDataWarning;
import com.homerun.domain.plan.type.OpenBankingIncomeSyncStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "오픈뱅킹 금융 요약을 계획 입력에 안전하게 동기화한 결과")
public record PlanFinancialSyncResponse(
        Long planId,
        FinancialSummaryStatus summaryStatus,
        ExternalDataCoverage balanceCoverage,
        ExternalDataCoverage transactionCoverage,

        @Schema(description = "최근 완료 3개월 모두 급여 거래가 확인된 경우의 월 실수령 추정값")
        Long suggestedMonthlyIncome,

        int salaryDetectedMonths,
        OpenBankingIncomeSyncStatus monthlyIncomeSyncStatus,

        @Schema(description = "등록 계좌의 출금가능액 합계. 가용현금으로 자동 저장하지 않음")
        BigDecimal totalAvailableBalance,

        @Schema(description = "오픈뱅킹만으로 순자산은 산출하지 않으므로 항상 null")
        BigDecimal suggestedNetAssets,

        BigDecimal averageMonthlyLoanRepayment,
        int loanCount,
        boolean incomplete,
        List<OpenBankingDataWarning> warnings,
        Instant fetchedAt,

        @Schema(description = "동기화 후 계획 입력. 적용할 소득이 없고 기존 입력도 없으면 null")
        PlanInputResponse input) {}
