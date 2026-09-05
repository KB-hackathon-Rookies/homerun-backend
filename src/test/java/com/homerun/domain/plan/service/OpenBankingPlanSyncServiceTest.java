package com.homerun.domain.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.homerun.domain.openbanking.dto.response.ExternalDataCoverage;
import com.homerun.domain.openbanking.dto.response.FinancialSnapshotResponse;
import com.homerun.domain.openbanking.dto.response.FinancialSummaryStatus;
import com.homerun.domain.openbanking.dto.response.OpenBankingFinancialSummaryResponse;
import com.homerun.domain.openbanking.service.FinancialSnapshotService;
import com.homerun.domain.openbanking.service.OpenBankingService;
import com.homerun.domain.openbanking.type.FinancialSnapshotSource;
import com.homerun.domain.plan.service.PlanInputService.OpenBankingIncomeSyncResult;
import com.homerun.domain.plan.type.OpenBankingIncomeSyncStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenBankingPlanSyncServiceTest {
    @Mock
    PlanInputService inputs;

    @Mock
    OpenBankingService openBanking;

    @Mock
    FinancialSnapshotService snapshots;

    private OpenBankingPlanSyncService service;

    @BeforeEach
    void setUp() {
        service = new OpenBankingPlanSyncService(inputs, openBanking, snapshots);
    }

    @Test
    void should_applyIncome_whenAllAccountsAndThreeSalaryMonthsAreCovered() {
        var summary = summary(new ExternalDataCoverage(2, 2), new BigDecimal("2900000"), 3);
        when(openBanking.financialSummary(1L, List.of("004"))).thenReturn(summary);
        var snapshot = new FinancialSnapshotResponse(
                3L,
                LocalDate.of(2026, 8, 31),
                FinancialSnapshotSource.OPEN_BANKING,
                false,
                15_000_000L,
                2_900_000L,
                null,
                null,
                300_000L,
                Instant.parse("2026-09-05T00:00:00Z"));
        when(snapshots.capture(1L, summary, 2_900_000L)).thenReturn(snapshot);
        when(inputs.syncOpenBankingIncome(1L, 10L, 2_900_000L))
                .thenReturn(new OpenBankingIncomeSyncResult(OpenBankingIncomeSyncStatus.APPLIED, null));

        var result = service.sync(1L, 10L, List.of("004"));

        verify(inputs).verifyOwner(1L, 10L);
        assertThat(result.suggestedMonthlyIncome()).isEqualTo(2_900_000L);
        assertThat(result.totalAvailableBalance()).isEqualByComparingTo("14000000");
        assertThat(result.suggestedNetAssets()).isNull();
        assertThat(result.snapshot()).isSameAs(snapshot);
    }

    @Test
    void should_notApplyIncome_whenCoverageOrSalaryMonthsAreIncomplete() {
        var summary = summary(new ExternalDataCoverage(2, 1), new BigDecimal("3000000"), 1);
        when(openBanking.financialSummary(1L, null)).thenReturn(summary);
        when(inputs.syncOpenBankingIncome(1L, 10L, null))
                .thenReturn(new OpenBankingIncomeSyncResult(OpenBankingIncomeSyncStatus.NOT_APPLICABLE, null));

        var result = service.sync(1L, 10L, null);

        assertThat(result.suggestedMonthlyIncome()).isNull();
        assertThat(result.monthlyIncomeSyncStatus()).isEqualTo(OpenBankingIncomeSyncStatus.NOT_APPLICABLE);
    }

    @Test
    void should_checkOwnershipBeforeCallingOpenBanking() {
        doThrow(new BusinessException(ErrorCode.PLAN_ACCESS_DENIED))
                .when(inputs)
                .verifyOwner(2L, 10L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sync(2L, 10L, null))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(openBanking);
        verifyNoInteractions(snapshots);
    }

    private OpenBankingFinancialSummaryResponse summary(
            ExternalDataCoverage transactionCoverage, BigDecimal income, int salaryMonths) {
        return new OpenBankingFinancialSummaryResponse(
                FinancialSummaryStatus.COMPLETE,
                new ExternalDataCoverage(2, 2),
                transactionCoverage,
                new ExternalDataCoverage(2, 2),
                2,
                new BigDecimal("15000000"),
                new BigDecimal("14000000"),
                income,
                salaryMonths,
                new BigDecimal("300000"),
                2,
                0,
                false,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 8, 31),
                3,
                List.of("004", "020"),
                List.of(),
                List.of(),
                Instant.parse("2026-09-05T00:00:00Z"));
    }
}
