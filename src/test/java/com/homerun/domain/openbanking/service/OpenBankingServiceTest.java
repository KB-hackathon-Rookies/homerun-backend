package com.homerun.domain.openbanking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.openbanking.config.OpenBankingTokenCipher;
import com.homerun.domain.openbanking.dto.response.FinancialSummaryStatus;
import com.homerun.domain.openbanking.dto.response.OpenBankingConnectionResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingFinancialSummaryResponse;
import com.homerun.domain.openbanking.entity.OpenBankingConnection;
import com.homerun.domain.openbanking.repository.OpenBankingConnectionRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.openbanking.OpenBankingClient;
import com.homerun.global.external.openbanking.OpenBankingResponses;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenBankingServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private OpenBankingConnectionRepository repository;

    @Mock
    private OpenBankingClient client;

    @Mock
    private OpenBankingTokenCipher cipher;

    private OpenBankingService service;

    @BeforeEach
    void setUp() {
        service = new OpenBankingService(repository, client, cipher, CLOCK);
    }

    @Test
    void should_encryptAndSaveTokens_whenConnecting() {
        OpenBankingResponses.Token token = new OpenBankingResponses.Token(
                "access", "refresh", "Bearer", "login inquiry", "1100000000", 3600, 7200L);
        when(client.exchangeAuthorizationCode("code")).thenReturn(token);
        when(cipher.encrypt(MEMBER_ID, "access")).thenReturn("encrypted-access");
        when(cipher.encrypt(MEMBER_ID, "refresh")).thenReturn("encrypted-refresh");
        when(repository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(OpenBankingConnection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OpenBankingConnectionResponse response = service.connect(MEMBER_ID, "code");

        assertThat(response.connected()).isTrue();
        assertThat(response.scope()).isEqualTo("login inquiry");
        verify(repository).save(any(OpenBankingConnection.class));
    }

    @Test
    void should_establishConnectionWithoutAuthorization_whenMockConnect() {
        when(cipher.encrypt(MEMBER_ID, "mock-access-token")).thenReturn("enc-access");
        when(cipher.encrypt(MEMBER_ID, "mock-refresh-token")).thenReturn("enc-refresh");
        when(repository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(OpenBankingConnection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OpenBankingConnectionResponse response = service.mockConnect(MEMBER_ID);

        // 금융결제원 토큰 교환을 부르지 않는다 — 인가 없이 연결만 세운다.
        verify(client, never()).exchangeAuthorizationCode(any());
        assertThat(response.connected()).isTrue();
        // 만료를 넉넉히 둬야 이후 accounts 가 리프레시로 새지 않는다.
        assertThat(response.accessTokenExpiresAt()).isAfter(NOW.plusSeconds(86_400));
        verify(repository).save(any(OpenBankingConnection.class));
    }

    @Test
    void should_refreshExpiredAccessToken_beforeFetchingAccounts() {
        OpenBankingConnection connection = connection(NOW.minusSeconds(1), NOW.plusSeconds(7200));
        when(repository.findByMemberIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(connection));
        when(cipher.decrypt(MEMBER_ID, "encrypted-refresh")).thenReturn("refresh");
        when(client.refreshToken("refresh"))
                .thenReturn(
                        new OpenBankingResponses.Token("new-access", "", "Bearer", "login inquiry", "", 3600, null));
        when(cipher.encrypt(MEMBER_ID, "new-access")).thenReturn("new-encrypted-access");
        when(cipher.encrypt(MEMBER_ID, "refresh")).thenReturn("encrypted-refresh");
        when(client.userInfo("new-access", "1100000000"))
                .thenReturn(new OpenBankingResponses.UserInfo("1100000000", "홍길동", List.of()));

        service.accounts(MEMBER_ID);

        verify(client).refreshToken("refresh");
        verify(repository).save(connection);
    }

    @Test
    void should_rejectFintechUseNumber_notOwnedByConnectedUser() {
        OpenBankingConnection connection = connection(NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        when(repository.findByMemberIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(connection));
        when(cipher.decrypt(MEMBER_ID, "encrypted-access")).thenReturn("access");
        when(client.userInfo("access", "1100000000"))
                .thenReturn(new OpenBankingResponses.UserInfo(
                        "1100000000",
                        "홍길동",
                        List.of(new OpenBankingResponses.Account(
                                "급여통장", "004", "국민은행", "", "111111111111111111111111", "123-***", "홍길동", "1"))));

        assertThatThrownBy(() -> service.balance(MEMBER_ID, "222222222222222222222222"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPEN_BANKING_ACCOUNT_NOT_FOUND));
    }

    @Test
    void should_rejectFutureTransactionPeriod() {
        assertThatThrownBy(() -> service.transactions(
                        MEMBER_ID,
                        "123456789012345678901234",
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 4),
                        null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_TRANSACTION_PERIOD));
    }

    @Test
    void should_aggregateBalancesSalaryAndLoanRepayments_forLastThreeCompletedMonths() {
        OpenBankingConnection connection = connection(NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        OpenBankingResponses.Account salaryAccount = account("004", "국민은행", "111111111111111111111111");
        OpenBankingResponses.Account savingsAccount = account("020", "우리은행", "222222222222222222222222");
        when(repository.findByMemberIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(connection));
        when(cipher.decrypt(MEMBER_ID, "encrypted-access")).thenReturn("access");
        when(client.userInfo("access", "1100000000"))
                .thenReturn(
                        new OpenBankingResponses.UserInfo("1100000000", "홍길동", List.of(salaryAccount, savingsAccount)));
        when(client.balance("access", salaryAccount.fintechUseNumber()))
                .thenReturn(balance(salaryAccount.fintechUseNumber(), "10000000", "9000000"));
        when(client.balance("access", savingsAccount.fintechUseNumber()))
                .thenReturn(balance(savingsAccount.fintechUseNumber(), "5000000", "5000000"));

        LocalDate fromDate = LocalDate.of(2026, 6, 1);
        LocalDate toDate = LocalDate.of(2026, 8, 31);
        when(client.transactions("access", salaryAccount.fintechUseNumber(), fromDate, toDate, null))
                .thenReturn(transactionPage(true, "next-1", salary(LocalDate.of(2026, 6, 25), "2800000")));
        when(client.transactions("access", salaryAccount.fintechUseNumber(), fromDate, toDate, "next-1"))
                .thenReturn(transactionPage(false, "", salary(LocalDate.of(2026, 7, 25), "2900000")));
        when(client.transactions("access", savingsAccount.fintechUseNumber(), fromDate, toDate, null))
                .thenReturn(transactionPage(false, "", salary(LocalDate.of(2026, 8, 25), "3000000")));

        OpenBankingResponses.Loan accessibleLoan =
                new OpenBankingResponses.Loan("004", "국민은행", "1234567890", "001", "123-***", "신용대출", "3100", "01");
        OpenBankingResponses.Loan inaccessibleLoan =
                new OpenBankingResponses.Loan("020", "우리은행", "", "", "456-***", "전세대출", "3170", "01");
        when(client.loans("access", "1100000000", "004", null))
                .thenReturn(new OpenBankingResponses.LoanPage(false, "", List.of(accessibleLoan), NOW));
        when(client.loans("access", "1100000000", "020", null))
                .thenReturn(new OpenBankingResponses.LoanPage(false, "", List.of(inaccessibleLoan), NOW));
        when(client.loanBasic("access", "1100000000", accessibleLoan, fromDate, toDate, null))
                .thenReturn(new OpenBankingResponses.LoanBasicPage(
                        null,
                        "01",
                        "004",
                        null,
                        false,
                        "",
                        List.of(
                                new OpenBankingResponses.LoanTransaction(
                                        LocalDate.of(2026, 6, 25), LocalTime.NOON, "02", new BigDecimal("-450000")),
                                new OpenBankingResponses.LoanTransaction(
                                        LocalDate.of(2026, 7, 25), LocalTime.NOON, "02", new BigDecimal("-450000")),
                                new OpenBankingResponses.LoanTransaction(
                                        LocalDate.of(2026, 8, 1), LocalTime.NOON, "01", new BigDecimal("10000000"))),
                        NOW));

        OpenBankingFinancialSummaryResponse response = service.financialSummary(MEMBER_ID, null);

        assertThat(response.totalAccountBalance()).isEqualByComparingTo("15000000");
        assertThat(response.totalAvailableBalance()).isEqualByComparingTo("14000000");
        assertThat(response.averageMonthlyNetIncome()).isEqualByComparingTo("2900000");
        assertThat(response.salaryDetectedMonths()).isEqualTo(3);
        assertThat(response.averageMonthlyLoanRepayment()).isEqualByComparingTo("300000");
        assertThat(response.loanCount()).isEqualTo(2);
        assertThat(response.loanRepaymentDetailUnavailableCount()).isEqualTo(1);
        assertThat(response.incomplete()).isTrue();
        assertThat(response.calculationFromDate()).isEqualTo(fromDate);
        assertThat(response.calculationToDate()).isEqualTo(toDate);
    }

    @Test
    void should_returnPartialSummary_whenOneDataSourceFails() {
        OpenBankingConnection connection = connection(NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        OpenBankingResponses.Account account = account("004", "국민은행", "111111111111111111111111");
        LocalDate fromDate = LocalDate.of(2026, 6, 1);
        LocalDate toDate = LocalDate.of(2026, 8, 31);
        when(repository.findByMemberIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(connection));
        when(cipher.decrypt(MEMBER_ID, "encrypted-access")).thenReturn("access");
        when(client.userInfo("access", "1100000000"))
                .thenReturn(new OpenBankingResponses.UserInfo("1100000000", "홍길동", List.of(account)));
        when(client.balance("access", account.fintechUseNumber()))
                .thenThrow(new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR));
        when(client.transactions("access", account.fintechUseNumber(), fromDate, toDate, null))
                .thenReturn(transactionPage(false, "", salary(LocalDate.of(2026, 8, 25), "3000000")));
        when(client.loans("access", "1100000000", "004", null))
                .thenReturn(new OpenBankingResponses.LoanPage(false, "", List.of(), NOW));

        OpenBankingFinancialSummaryResponse response = service.financialSummary(MEMBER_ID, null);

        assertThat(response.status()).isEqualTo(FinancialSummaryStatus.PARTIAL);
        assertThat(response.accountBalanceCoverage().requested()).isEqualTo(1);
        assertThat(response.accountBalanceCoverage().succeeded()).isZero();
        assertThat(response.accountTransactionCoverage().succeeded()).isEqualTo(1);
        assertThat(response.averageMonthlyNetIncome()).isEqualByComparingTo("3000000");
        assertThat(response.warnings())
                .singleElement()
                .satisfies(warning -> assertThat(warning.code()).isEqualTo("BALANCE_UNAVAILABLE"));
    }

    @Test
    void should_returnUnavailableSummary_whenAllRequestedSourcesFail() {
        OpenBankingConnection connection = connection(NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        OpenBankingResponses.Account account = account("004", "국민은행", "111111111111111111111111");
        LocalDate fromDate = LocalDate.of(2026, 6, 1);
        LocalDate toDate = LocalDate.of(2026, 8, 31);
        when(repository.findByMemberIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(connection));
        when(cipher.decrypt(MEMBER_ID, "encrypted-access")).thenReturn("access");
        when(client.userInfo("access", "1100000000"))
                .thenReturn(new OpenBankingResponses.UserInfo("1100000000", "홍길동", List.of(account)));
        when(client.balance("access", account.fintechUseNumber()))
                .thenThrow(new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR));
        when(client.transactions("access", account.fintechUseNumber(), fromDate, toDate, null))
                .thenThrow(new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR));
        when(client.loans("access", "1100000000", "004", null))
                .thenThrow(new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR));

        OpenBankingFinancialSummaryResponse response = service.financialSummary(MEMBER_ID, null);

        assertThat(response.status()).isEqualTo(FinancialSummaryStatus.UNAVAILABLE);
        assertThat(response.totalAccountBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.averageMonthlyNetIncome()).isNull();
        assertThat(response.averageMonthlyLoanRepayment()).isNull();
        assertThat(response.warnings()).hasSize(3);
        assertThat(response.incomplete()).isTrue();
    }

    private OpenBankingResponses.Account account(String bankCode, String bankName, String fintechUseNumber) {
        return new OpenBankingResponses.Account("통장", bankCode, bankName, "", fintechUseNumber, "123-***", "홍길동", "1");
    }

    private OpenBankingResponses.Balance balance(String fintechUseNumber, String balance, String available) {
        return new OpenBankingResponses.Balance(
                "은행",
                "",
                fintechUseNumber,
                new BigDecimal(balance),
                new BigDecimal(available),
                "1",
                "통장",
                null,
                null,
                null,
                NOW);
    }

    private OpenBankingResponses.TransactionPage transactionPage(
            boolean hasNextPage, String traceInfo, OpenBankingResponses.Transaction... transactions) {
        return new OpenBankingResponses.TransactionPage(
                "은행", "", "", BigDecimal.ZERO, hasNextPage, traceInfo, List.of(transactions), NOW);
    }

    private OpenBankingResponses.Transaction salary(LocalDate date, String amount) {
        return new OpenBankingResponses.Transaction(
                date, LocalTime.NOON, "입금", "급여", "홈런", new BigDecimal(amount), BigDecimal.ZERO, "");
    }

    private OpenBankingConnection connection(Instant accessExpiresAt, Instant refreshExpiresAt) {
        OpenBankingConnection connection = OpenBankingConnection.create(MEMBER_ID);
        connection.updateCredentials(
                "1100000000",
                "encrypted-access",
                "encrypted-refresh",
                "Bearer",
                "login inquiry",
                accessExpiresAt,
                refreshExpiresAt,
                NOW.minusSeconds(60));
        return connection;
    }
}
