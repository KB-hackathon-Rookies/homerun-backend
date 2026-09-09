package com.homerun.domain.openbanking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.openbanking.config.OpenBankingTokenCipher;
import com.homerun.domain.openbanking.dto.response.AccountBalanceBreakdownResponse;
import com.homerun.domain.openbanking.dto.response.FinancialSummaryStatus;
import com.homerun.domain.openbanking.dto.response.MonthlyIncomeResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingAccountResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingFinancialSummaryResponse;
import com.homerun.domain.openbanking.entity.OpenBankingConnection;
import com.homerun.domain.openbanking.repository.OpenBankingConnectionRepository;
import com.homerun.domain.openbanking.type.Persona;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.openbanking.HttpOpenBankingClient;
import com.homerun.global.external.openbanking.MockDataOpenBankingClient;
import com.homerun.global.external.openbanking.MockPersonaSelection;
import com.homerun.global.external.openbanking.OpenBankingClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 플래그를 켰을 때 financial-summary 집계까지 페르소나 한 명의 프로필로 일관되게 채워지는지, 그리고
 * 연결이 없는 회원은 모킹이 켜져 있어도 지금과 똑같이 실패하는지 확인한다. 플래그를 껐을 때 실제
 * 업스트림 경로가 그대로인지는 OpenBankingRealClientPathTest 가 본다.
 *
 * <p>여기서 보는 값은 전부 {@link Persona} 에서 나온다. 요약이 페르소나와 다른 숫자를 내면 자산확인
 * 화면과 진단 입력이 같은 사람을 두고 다른 이야기를 하게 된다 — 그게 이 테스트가 잡는 지점이다.
 */
class OpenBankingMockDataSummaryTest {

    private static final Long MEMBER_ID = 1L;
    private static final Persona KIM = Persona.KIM_KUKMIN;
    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OpenBankingConnectionRepository repository;
    private OpenBankingTokenCipher cipher;
    private HttpOpenBankingClient authorizationDelegate;
    private MockPersonaSelection personaSelection;
    private OpenBankingService mockDataService;

    @BeforeEach
    void setUp() {
        repository = mock(OpenBankingConnectionRepository.class);
        cipher = mock(OpenBankingTokenCipher.class);
        authorizationDelegate = mock(HttpOpenBankingClient.class);
        personaSelection = new MockPersonaSelection(Persona.KIM_KUKMIN);
        mockDataService = service(new MockDataOpenBankingClient(authorizationDelegate, CLOCK, personaSelection));
    }

    @Test
    void should_aggregateTheDefaultPersona_intoACoherentFinancialSummary() {
        connected();

        OpenBankingFinancialSummaryResponse summary = mockDataService.financialSummary(MEMBER_ID, null);

        assertThat(summary.status()).isEqualTo(FinancialSummaryStatus.COMPLETE);
        assertThat(summary.warnings()).isEmpty();
        assertThat(summary.incomplete()).isFalse();
        assertThat(summary.connectedAccountCount()).isEqualTo(3);
        assertThat(summary.accountBalanceCoverage().complete()).isTrue();
        assertThat(summary.accountTransactionCoverage().complete()).isTrue();
        // 페르소나를 고른 적 없으면 시연 기본인 김국민이다. 아래 값은 전부 Persona.KIM_KUKMIN 에서 나온다.
        assertThat(summary.totalAccountBalance()).isEqualByComparingTo(won(KIM.getFinancialAsset()));
        assertThat(summary.totalAvailableBalance()).isEqualByComparingTo(won(KIM.getFinancialAsset()));
        // 1루 진단이 "오픈뱅킹으로 조회한 월 평균 소득"으로 보여 주는 값. 거래내역의 급여 입금과 같아야 한다.
        assertThat(summary.averageMonthlyNetIncome()).isEqualByComparingTo(won(KIM.getMonthlyIncome()));
        assertThat(summary.salaryDetectedMonths()).isEqualTo(3);
        // 계좌별 분해가 합계와 맞아야 한다 — 김국민은 계좌 3개(급여·적금·청약)이고 합이 총 금융자산이다.
        assertThat(summary.accountBalances()).hasSize(3);
        assertThat(summary.accountBalances().stream()
                        .map(AccountBalanceBreakdownResponse::balanceAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(won(KIM.getFinancialAsset()));
        // 월별 소득 분해 — 집계 기간 세 달이 오름차순으로 온다.
        assertThat(summary.monthlyNetIncomes())
                .hasSize(3)
                .extracting(MonthlyIncomeResponse::month)
                .containsExactly("2026-06", "2026-07", "2026-08");
        /*
         * 달마다 금액이 다르다(기본급·시간외수당·분기 성과급). 매달 같으면 화면에서 가짜로 읽힌다.
         * 대신 세 달 합이 월소득의 세 배라 평균이 페르소나 값과 정확히 맞는다.
         */
        assertThat(summary.monthlyNetIncomes())
                .extracting(MonthlyIncomeResponse::amount)
                .doesNotHaveDuplicates();
        assertThat(summary.monthlyNetIncomes().stream()
                        .map(MonthlyIncomeResponse::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(won(KIM.getMonthlyIncome() * 3));
        // 빚이 없는 페르소나라 대출 목록이 비어 있다. 0원짜리 대출 행을 만들면 여기서 개수가 어긋난다.
        assertThat(summary.loanCount()).isZero();
        assertThat(summary.averageMonthlyLoanRepayment()).isEqualByComparingTo(won(KIM.getMonthlyDebtPayment()));
        assertThat(summary.loanRepaymentDetailUnavailableCount()).isZero();
        assertThat(summary.calculationFromDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(summary.calculationToDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void should_aggregateTheSyncedPersona_whenTheMemberChoseAnotherOne() {
        connectedAsMock();
        personaSelection.select(MEMBER_ID, Persona.PARK_SENIOR);

        OpenBankingFinancialSummaryResponse summary = mockDataService.financialSummary(MEMBER_ID, null);

        assertThat(summary.status()).isEqualTo(FinancialSummaryStatus.COMPLETE);
        assertThat(summary.totalAccountBalance()).isEqualByComparingTo(won(Persona.PARK_SENIOR.getFinancialAsset()));
        assertThat(summary.averageMonthlyNetIncome()).isEqualByComparingTo(won(Persona.PARK_SENIOR.getMonthlyIncome()));
        assertThat(summary.loanCount()).isEqualTo(1);
        assertThat(summary.averageMonthlyLoanRepayment())
                .isEqualByComparingTo(won(Persona.PARK_SENIOR.getMonthlyDebtPayment()));
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
                // 급여가 달마다 달라 한 건씩 비교할 수 없다. 요약이 평균을 내므로 합으로 맞춘다.
                .satisfies(salaries -> assertThat(salaries.stream()
                                .map(salary -> salary.amount())
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .isEqualByComparingTo(summary.averageMonthlyNetIncome().multiply(BigDecimal.valueOf(3))));
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

    private BigDecimal won(long value) {
        return BigDecimal.valueOf(value);
    }

    /** 가짜 연결이 세우는 사용자일련번호. 목 클라이언트는 여기서 회원을 되짚어 페르소나를 고른다. */
    private void connectedAsMock() {
        connect(MockPersonaSelection.MOCK_USER_SEQ_NO_PREFIX + MEMBER_ID);
    }

    private void connected() {
        connect("1100000000");
    }

    private void connect(String userSeqNo) {
        OpenBankingConnection connection = OpenBankingConnection.create(MEMBER_ID);
        connection.updateCredentials(
                userSeqNo,
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
