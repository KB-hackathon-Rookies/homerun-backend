package com.homerun.domain.openbanking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.homerun.domain.openbanking.dto.response.ExternalDataCoverage;
import com.homerun.domain.openbanking.dto.response.FinancialSummaryStatus;
import com.homerun.domain.openbanking.dto.response.OpenBankingFinancialSummaryResponse;
import com.homerun.domain.openbanking.entity.FinancialSnapshot;
import com.homerun.domain.openbanking.repository.FinancialSnapshotRepository;
import com.homerun.domain.openbanking.type.FinancialSnapshotSource;
import com.homerun.domain.openbanking.type.Persona;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialSnapshotServiceTest {

    @Mock
    FinancialSnapshotRepository snapshots;

    private FinancialSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new FinancialSnapshotService(snapshots);
    }

    @Test
    void should_captureOnlyReliableValues_whenSummaryCoverageIsComplete() {
        echoSavedSnapshot();
        var result = service.capture(
                7L, summary(new ExternalDataCoverage(2, 2), new ExternalDataCoverage(2, 2), 0), 2_900_000L);

        assertThat(result.asOf()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(result.financialAsset()).isEqualTo(15_000_000L);
        assertThat(result.monthlyIncome()).isEqualTo(2_900_000L);
        assertThat(result.monthlyDebtPayment()).isEqualTo(300_000L);
        assertThat(result.monthlyExpense()).isNull();
        assertThat(result.loanBalance()).isNull();
        assertThat(result.confirmedByUser()).isFalse();
    }

    @Test
    void should_omitValues_whenBalanceOrLoanCoverageIsIncomplete() {
        echoSavedSnapshot();
        var result = service.capture(
                7L, summary(new ExternalDataCoverage(2, 1), new ExternalDataCoverage(2, 2), 1), 2_900_000L);

        assertThat(result.financialAsset()).isNull();
        assertThat(result.monthlyIncome()).isEqualTo(2_900_000L);
        assertThat(result.monthlyDebtPayment()).isNull();
    }

    @Test
    void should_notStoreEmptySnapshot_whenNoValueHasCompleteCoverage() {
        var result =
                service.capture(7L, summary(new ExternalDataCoverage(2, 1), new ExternalDataCoverage(2, 1), 1), null);

        assertThat(result).isNull();
        verifyNoInteractions(snapshots);
    }

    @Test
    void should_persistPersonaValuesAsMock_whenMockConnecting() {
        echoSavedSnapshot();

        var result = service.connectMock(7L, Persona.LEE_TIGHT);

        assertThat(result.source()).isEqualTo(FinancialSnapshotSource.MOCK);
        assertThat(result.confirmedByUser()).isFalse();
        assertThat(result.financialAsset()).isEqualTo(Persona.LEE_TIGHT.getFinancialAsset());
        assertThat(result.monthlyIncome()).isEqualTo(Persona.LEE_TIGHT.getMonthlyIncome());
        assertThat(result.monthlyExpense()).isEqualTo(Persona.LEE_TIGHT.getMonthlyExpense());
        assertThat(result.loanBalance()).isEqualTo(Persona.LEE_TIGHT.getLoanBalance());
        assertThat(result.monthlyDebtPayment()).isEqualTo(Persona.LEE_TIGHT.getMonthlyDebtPayment());
        assertThat(result.asOf()).isEqualTo(LocalDate.now());
    }

    @Test
    void should_returnLatestSnapshot_withoutCallingProvider() {
        FinancialSnapshot snapshot = FinancialSnapshot.fromOpenBanking(
                7L,
                LocalDate.of(2026, 8, 31),
                10_000_000L,
                3_000_000L,
                250_000L,
                Instant.parse("2026-09-05T00:00:00Z"));
        when(snapshots.findFirstByUserIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(Optional.of(snapshot));

        assertThat(service.latest(7L).monthlyIncome()).isEqualTo(3_000_000L);
    }

    @Test
    void should_throwNotFound_whenSnapshotDoesNotExist() {
        when(snapshots.findFirstByUserIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.latest(7L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.OPEN_BANKING_FINANCIAL_SNAPSHOT_NOT_FOUND));
    }

    private void echoSavedSnapshot() {
        when(snapshots.save(any(FinancialSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private OpenBankingFinancialSummaryResponse summary(
            ExternalDataCoverage balanceCoverage,
            ExternalDataCoverage loanCoverage,
            int loanRepaymentUnavailableCount) {
        return new OpenBankingFinancialSummaryResponse(
                FinancialSummaryStatus.COMPLETE,
                balanceCoverage,
                new ExternalDataCoverage(2, 2),
                loanCoverage,
                2,
                new BigDecimal("15000000"),
                new BigDecimal("14000000"),
                new BigDecimal("2900000"),
                3,
                new BigDecimal("300000"),
                2,
                loanRepaymentUnavailableCount,
                loanRepaymentUnavailableCount > 0,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 8, 31),
                3,
                List.of("004", "020"),
                List.of(),
                List.of(),
                Instant.parse("2026-09-05T00:00:00Z"));
    }
}
