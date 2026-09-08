package com.homerun.domain.openbanking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.openbanking.config.OpenBankingTokenCipher;
import com.homerun.domain.openbanking.dto.response.FinancialSummaryStatus;
import com.homerun.domain.openbanking.dto.response.OpenBankingAccountResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingFinancialSummaryResponse;
import com.homerun.domain.openbanking.entity.OpenBankingConnection;
import com.homerun.domain.openbanking.repository.OpenBankingConnectionRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.openbanking.HttpOpenBankingClient;
import com.homerun.global.external.openbanking.MockDataOpenBankingClient;
import com.homerun.global.external.openbanking.OpenBankingClient;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 플래그를 켰을 때 financial-summary 집계까지 샘플 프로필로 일관되게 채워지는지, 그리고 연결이 없는
 * 회원은 모킹이 켜져 있어도 지금과 똑같이 실패하는지 확인한다. 플래그를 껐을 때 실제 업스트림 경로가
 * 그대로인지는 OpenBankingRealClientPathTest 가 본다.
 */
class OpenBankingMockDataSummaryTest {

    private static final Long MEMBER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OpenBankingConnectionRepository repository;
    private OpenBankingTokenCipher cipher;
    private HttpOpenBankingClient authorizationDelegate;
    private OpenBankingService mockDataService;

    @BeforeEach
    void setUp() {
        repository = mock(OpenBankingConnectionRepository.class);
        cipher = mock(OpenBankingTokenCipher.class);
        authorizationDelegate = mock(HttpOpenBankingClient.class);
        mockDataService = service(new MockDataOpenBankingClient(authorizationDelegate, CLOCK));
    }

    @Test
    void should_aggregateSampleProfile_intoACoherentFinancialSummary() {
        connected();

        OpenBankingFinancialSummaryResponse summary = mockDataService.financialSummary(MEMBER_ID, null);

        assertThat(summary.status()).isEqualTo(FinancialSummaryStatus.COMPLETE);
        assertThat(summary.warnings()).isEmpty();
        assertThat(summary.incomplete()).isFalse();
        assertThat(summary.connectedAccountCount()).isEqualTo(3);
        assertThat(summary.accountBalanceCoverage().complete()).isTrue();
        assertThat(summary.accountTransactionCoverage().complete()).isTrue();
        // 잔액 4,000만원 = 급여통장 320만 + 자유적금 2,480만 + 주택청약 1,200만.
        assertThat(summary.totalAccountBalance()).isEqualByComparingTo("40000000");
        assertThat(summary.totalAvailableBalance()).isEqualByComparingTo("40000000");
        // 1루 진단이 "오픈뱅킹으로 조회한 월 평균 소득"으로 보여 주는 값. 거래내역의 급여 입금과 같아야 한다.
        assertThat(summary.averageMonthlyNetIncome()).isEqualByComparingTo("2800000");
        assertThat(summary.salaryDetectedMonths()).isEqualTo(3);
        assertThat(summary.averageMonthlyLoanRepayment()).isEqualByComparingTo("150000");
        assertThat(summary.loanCount()).isEqualTo(1);
        assertThat(summary.loanRepaymentDetailUnavailableCount()).isZero();
        assertThat(summary.calculationFromDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(summary.calculationToDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void should_matchSummaryIncome_withTheSalaryDepositsInTheTransactionList() {
        connected();

        OpenBankingFinancialSummaryResponse summary = mockDataService.financialSummary(MEMBER_ID, null);
        String salaryAccount = mockDataService.accounts(MEMBER_ID).stream()
                .map(OpenBankingAccountResponse::fintechUseNumber)
                .findFirst()
                .orElseThrow();

        assertThat(mockDataService
                        .transactions(
                                MEMBER_ID,
                                salaryAccount,
                                summary.calculationFromDate(),
                                summary.calculationToDate(),
                                null)
                        .transactions())
                .filteredOn(transaction -> "급여".equals(transaction.type()))
                .hasSize(3)
                .allSatisfy(
                        salary -> assertThat(salary.amount()).isEqualByComparingTo(summary.averageMonthlyNetIncome()));
    }

    @Test
    void should_surfaceSampleMarker_inTheAccountAliasShownByTheUi() {
        connected();

        assertThat(mockDataService.accounts(MEMBER_ID))
                .isNotEmpty()
                .allSatisfy(account -> assertThat(account.alias()).contains("샘플"));
    }

    @Test
    void should_failTheSameWay_forAMemberWithoutConnection_whenMockDataIsOn() {
        when(repository.findByMemberIdForUpdate(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mockDataService.financialSummary(MEMBER_ID, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPEN_BANKING_NOT_CONNECTED));
        assertThatThrownBy(() -> mockDataService.accounts(MEMBER_ID)).isInstanceOf(BusinessException.class);
        verify(authorizationDelegate, never()).refreshToken(anyString());
    }

    private OpenBankingService service(OpenBankingClient client) {
        return new OpenBankingService(repository, client, cipher, CLOCK);
    }

    private void connected() {
        OpenBankingConnection connection = OpenBankingConnection.create(MEMBER_ID);
        connection.updateCredentials(
                "1100000000",
                "encrypted-access",
                "encrypted-refresh",
                "Bearer",
                "login inquiry",
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200),
                NOW.minusSeconds(60));
        when(repository.findByMemberIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(connection));
        when(cipher.decrypt(MEMBER_ID, "encrypted-access")).thenReturn("access");
    }
}
