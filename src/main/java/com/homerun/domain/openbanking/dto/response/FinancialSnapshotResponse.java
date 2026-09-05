package com.homerun.domain.openbanking.dto.response;

import com.homerun.domain.openbanking.entity.FinancialSnapshot;
import com.homerun.domain.openbanking.type.FinancialSnapshotSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "저장된 금융정보 스냅샷")
public record FinancialSnapshotResponse(
        Long id,
        LocalDate asOf,
        FinancialSnapshotSource source,
        boolean confirmedByUser,

        @Schema(description = "조회가 완료된 등록계좌 잔액 합계이며 전체 순자산이 아님")
        Long financialAsset,

        @Schema(description = "최근 완료 3개월 모두 확인된 월평균 급여 입금액") Long monthlyIncome,

        @Schema(description = "현재 오픈뱅킹 요약으로 계산하지 않아 null") Long monthlyExpense,

        @Schema(description = "현재 오픈뱅킹 대출목록에서 잔액을 제공하지 않아 null")
        Long loanBalance,

        @Schema(description = "조회 가능한 대출의 최근 완료 3개월 월평균 상환액")
        Long monthlyDebtPayment,

        Instant capturedAt) {

    public static FinancialSnapshotResponse from(FinancialSnapshot snapshot) {
        return new FinancialSnapshotResponse(
                snapshot.getId(),
                snapshot.getAsOf(),
                snapshot.getSource(),
                snapshot.isConfirmedByUser(),
                snapshot.getFinancialAsset(),
                snapshot.getMonthlyIncome(),
                snapshot.getMonthlyExpense(),
                snapshot.getLoanBalance(),
                snapshot.getMonthlyDebtPayment(),
                snapshot.getCreatedAt());
    }
}
