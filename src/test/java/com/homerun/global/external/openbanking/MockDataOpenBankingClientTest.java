package com.homerun.global.external.openbanking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.openbanking.type.Persona;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.openbanking.OpenBankingResponses.Account;
import com.homerun.global.external.openbanking.OpenBankingResponses.Balance;
import com.homerun.global.external.openbanking.OpenBankingResponses.LoanBasicPage;
import com.homerun.global.external.openbanking.OpenBankingResponses.LoanPage;
import com.homerun.global.external.openbanking.OpenBankingResponses.Token;
import com.homerun.global.external.openbanking.OpenBankingResponses.Transaction;
import com.homerun.global.external.openbanking.OpenBankingResponses.TransactionPage;
import com.homerun.global.external.openbanking.OpenBankingResponses.UserInfo;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 데모 모킹의 두 계층이 같은 사람을 말하는지 고정한다.
 *
 * <p>조회 API 를 대신하는 이 클라이언트와, {@code financial_snapshot}·{@code plan_input} 에 값을 넣는
 * 페르소나 적재 경로는 원래 각자 숫자를 들고 있었다. 그래서 오픈뱅킹을 연결하면 4,000만원이,
 * 페르소나를 실으면 1,500만원이 나왔다. 아래 불변식들이 그 어긋남을 다시 만들지 못하게 막는다 —
 * 픽스처의 숫자 하나만 고쳐도 여기서 깨진다.
 */
class MockDataOpenBankingClientTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 8, 31);
    private static final int SUMMARY_MONTHS = 3;

    /** 월 단위 불변식(급여·지출·이체)은 완결된 한 달만 놓고 본다. */
    private static final LocalDate MONTH_FROM = LocalDate.of(2026, 8, 1);

    private static final LocalDate MONTH_TO = LocalDate.of(2026, 8, 31);

    private static final Long MEMBER_ID = 7L;
    private static final String USER_SEQ_NO = MockPersonaSelection.MOCK_USER_SEQ_NO_PREFIX + MEMBER_ID;

    private HttpOpenBankingClient authorizationDelegate;
    private MockPersonaSelection personaSelection;
    private MockDataOpenBankingClient client;

    @BeforeEach
    void setUp() {
        authorizationDelegate = mock(HttpOpenBankingClient.class);
        personaSelection = new MockPersonaSelection(Persona.KIM_KUKMIN);
        client = new MockDataOpenBankingClient(authorizationDelegate, CLOCK, personaSelection);
    }

    @Test
    void should_delegateAuthorizationCalls_toRealClient() {
        URI expectedUri = URI.create("https://testapi.openbanking.or.kr/oauth/2.0/authorize");
        Token token = new Token("access", "refresh", "Bearer", "login inquiry", "1100000000", 3600, 7200L);
        when(authorizationDelegate.authorizationUri("state")).thenReturn(expectedUri);
        when(authorizationDelegate.exchangeAuthorizationCode("code")).thenReturn(token);
        when(authorizationDelegate.refreshToken("refresh")).thenReturn(token);

        assertThat(client.authorizationUri("state")).isEqualTo(expectedUri);
        assertThat(client.exchangeAuthorizationCode("code")).isEqualTo(token);
        assertThat(client.refreshToken("refresh")).isEqualTo(token);

        verify(authorizationDelegate).authorizationUri("state");
        verify(authorizationDelegate).exchangeAuthorizationCode("code");
        verify(authorizationDelegate).refreshToken("refresh");
    }

    @Test
    @DisplayName("페르소나를 고른 적 없으면 시연 기본인 김국민으로 답한다")
    void should_serveKimKukmin_whenTheMemberHasNotChosenAPersona() {
        assertThat(personaSelection.defaultPersona()).isEqualTo(Persona.KIM_KUKMIN);

        UserInfo neverChose = client.userInfo("access", USER_SEQ_NO);
        UserInfo notAMockConnection = client.userInfo("access", "1100000000");

        assertThat(neverChose.userName()).isEqualTo(Persona.KIM_KUKMIN.getLabel());
        assertThat(notAMockConnection.userName()).isEqualTo(Persona.KIM_KUKMIN.getLabel());
        assertThat(totalBalance(neverChose)).isEqualByComparingTo(won(Persona.KIM_KUKMIN.getFinancialAsset()));
    }

    @Test
    @DisplayName("회원이 다른 페르소나를 실으면 그 회원의 조회 결과만 따라 바뀐다")
    void should_followThePersona_theMemberSyncedLast() {
        personaSelection.select(MEMBER_ID, Persona.PARK_SENIOR);

        assertThat(client.userInfo("access", USER_SEQ_NO).userName()).isEqualTo(Persona.PARK_SENIOR.getLabel());
        assertThat(client.userInfo("access", MockPersonaSelection.MOCK_USER_SEQ_NO_PREFIX + 99)
                        .userName())
                .isEqualTo(Persona.KIM_KUKMIN.getLabel());
    }

    @ParameterizedTest
    @EnumSource(Persona.class)
    @DisplayName("계좌는 실데이터와 구분되는 샘플 표식과 페르소나 이름을 달고 나온다")
    void should_returnAccounts_thatLookObviouslyLikeSampleData(Persona persona) {
        UserInfo userInfo = accountsOf(persona);

        assertThat(userInfo.userSeqNo()).isEqualTo(USER_SEQ_NO);
        assertThat(userInfo.accounts()).hasSize(3).allSatisfy(account -> {
            assertThat(account.accountAlias()).contains("샘플");
            assertThat(account.accountHolderName()).isEqualTo(persona.getLabel());
            assertThat(account.accountNumberMasked()).contains("*");
            assertThat(account.fintechUseNumber()).startsWith("SAMPLE-");
        });
    }

    @ParameterizedTest
    @EnumSource(Persona.class)
    @DisplayName("계좌 잔액 합계는 페르소나의 금융자산과 정확히 같다")
    void should_sumAccountBalances_toThePersonaFinancialAsset(Persona persona) {
        assertThat(totalBalance(accountsOf(persona))).isEqualByComparingTo(won(persona.getFinancialAsset()));
    }

    @ParameterizedTest
    @EnumSource(Persona.class)
    @DisplayName("급여 입금은 페르소나의 월소득과 같고, 한 계좌에만 들어와 이중집계되지 않는다")
    void should_payTheMonthlyIncome_intoExactlyOneAccount(Persona persona) {
        List<Account> accounts = accountsOf(persona).accounts();

        long accountsWithSalary = accounts.stream()
                .map(account -> transactions(account, FROM_DATE, TO_DATE))
                .filter(transactions -> transactions.stream().anyMatch(this::isSalary))
                .count();
        List<Transaction> salaries = accounts.stream()
                .flatMap(account -> transactions(account, FROM_DATE, TO_DATE).stream())
                .filter(this::isSalary)
                .toList();

        assertThat(accountsWithSalary).isEqualTo(1);
        assertThat(salaries)
                .hasSize(SUMMARY_MONTHS)
                .allSatisfy(salary -> assertThat(salary.direction()).isEqualTo("입금"));
        /*
         * 매달 같은 금액이 아니라 **평균**이 페르소나 값과 같아야 한다. 요약 집계가 최근 3개월을
         * 평균 내므로, 이 합이 어긋나면 화면이 페르소나와 다른 소득을 말한다.
         */
        BigDecimal total = salaries.stream().map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo(won(persona.getMonthlyIncome() * SUMMARY_MONTHS));
        assertThat(salaries)
                .extracting(Transaction::date)
                .containsExactlyInAnyOrder(
                        LocalDate.of(2026, 8, 25), LocalDate.of(2026, 7, 25), LocalDate.of(2026, 6, 25));
    }

    @Test
    @DisplayName("기본 페르소나의 급여는 달마다 다르다 — 매달 같은 금액이면 화면에서 가짜로 읽힌다")
    void should_varyTheSalary_acrossMonths() {
        List<BigDecimal> amounts = accountsOf(Persona.KIM_KUKMIN).accounts().stream()
                .flatMap(account -> transactions(account, FROM_DATE, TO_DATE).stream())
                .filter(this::isSalary)
                .map(Transaction::amount)
                .toList();

        assertThat(amounts).hasSize(SUMMARY_MONTHS);
        assertThat(amounts.stream().distinct()).hasSize(SUMMARY_MONTHS);
    }

    @ParameterizedTest
    @EnumSource(Persona.class)
    @DisplayName("한 달치 소비 출금 합계는 페르소나의 월지출과 같다")
    void should_withdrawTheMonthlyExpense_acrossTheSpendingTransactions(Persona persona) {
        BigDecimal expenses = monthTransactions(persona).stream()
                .filter(this::isExpense)
                .map(Transaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(expenses).isEqualByComparingTo(won(persona.getMonthlyExpense()));
    }

    @ParameterizedTest
    @EnumSource(Persona.class)
    @DisplayName("적금·청약 자동이체는 본인 계좌 사이를 오갈 뿐 순자산을 바꾸지 않는다")
    void should_moveSavingsBetweenOwnAccounts_withoutChangingNetWorth(Persona persona) {
        List<Transaction> transfers = monthTransactions(persona).stream()
                .filter(transaction -> MockPersonaFixtures.TYPE_INTERNAL_TRANSFER.equals(transaction.type()))
                .toList();

        assertThat(transfers).isNotEmpty();
        assertThat(transfers.stream().map(this::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transfers.stream()
                        .filter(transaction -> "출금".equals(transaction.direction()))
                        .map(transaction ->
                                transaction.date() + "/" + transaction.amount().toPlainString())
                        .sorted()
                        .toList())
                .isEqualTo(transfers.stream()
                        .filter(transaction -> "입금".equals(transaction.direction()))
                        .map(transaction ->
                                transaction.date() + "/" + transaction.amount().toPlainString())
                        .sorted()
                        .toList());
    }

    @ParameterizedTest
    @EnumSource(Persona.class)
    @DisplayName("급여통장의 월 현금흐름은 0으로 닫힌다 — 급여 = 지출 + 저축이체 + 대출상환")
    void should_keepTheSalaryAccountFlat_becauseInflowMatchesOutflow(Persona persona) {
        Account salaryAccount = salaryAccount(persona);

        List<Transaction> transactions = transactions(salaryAccount, MONTH_FROM, MONTH_TO);

        assertThat(transactions.stream().map(this::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(client.balance("access", salaryAccount.fintechUseNumber()).balanceAmount())
                .isEqualByComparingTo(transactions.get(0).balanceAfterTransaction());
    }

    @ParameterizedTest
    @EnumSource(Persona.class)
    @DisplayName("거래내역의 최신 잔액은 잔액 조회 결과와 어긋나지 않는다")
    void should_endTransactionSeries_atTheBalanceReportedByBalanceLookup(Persona persona) {
        for (Account account : accountsOf(persona).accounts()) {
            TransactionPage page = client.transactions("access", account.fintechUseNumber(), FROM_DATE, TO_DATE, null);
            Balance balance = client.balance("access", account.fintechUseNumber());

            assertThat(page.currentBalance()).isEqualByComparingTo(balance.balanceAmount());
            assertThat(page.transactions().get(0).balanceAfterTransaction())
                    .isEqualByComparingTo(balance.balanceAmount());
        }
    }

    @ParameterizedTest
    @EnumSource(Persona.class)
    @DisplayName("대출은 페르소나의 대출잔액·월상환액을 따르고, 빚이 없으면 목록이 비어 있다")
    void should_reportLoans_thatMatchThePersonaDebt(Persona persona) {
        accountsOf(persona);
        LoanPage loans = client.loans("access", USER_SEQ_NO, MockPersonaFixtures.BANK_CODE, null);
        BigDecimal repaymentsInAccount = monthTransactions(persona).stream()
                .filter(transaction -> MockPersonaFixtures.TYPE_DEBT_REPAYMENT.equals(transaction.type()))
                .map(Transaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(repaymentsInAccount).isEqualByComparingTo(won(persona.getMonthlyDebtPayment()));
        if (persona.getLoanBalance() == 0L) {
            // 0원짜리 대출 행은 화면에 없는 부채를 그린다. 빚이 없으면 목록 자체가 비어야 한다.
            assertThat(loans.loans()).isEmpty();
            return;
        }
        assertThat(loans.loans()).singleElement().satisfies(loan -> {
            assertThat(loan.productName()).contains("샘플");
            assertThat(loan.accountType()).isEqualTo("3170");
            assertThat(loan.accountNumber()).isNotBlank();
        });
        LoanBasicPage page =
                client.loanBasic("access", USER_SEQ_NO, loans.loans().get(0), FROM_DATE, TO_DATE, null);
        assertThat(page.hasNextPage()).isFalse();
        assertThat(page.transactions()).hasSize(SUMMARY_MONTHS).allSatisfy(repayment -> {
            assertThat(repayment.type()).isEqualTo("02");
            assertThat(repayment.amount()).isEqualByComparingTo(won(persona.getMonthlyDebtPayment()));
        });
    }

    @Test
    @DisplayName("샘플 계좌를 들고 있지 않은 금융기관에는 대출이 없다")
    void should_returnNoLoans_forABankWithoutSampleAccounts() {
        personaSelection.select(MEMBER_ID, Persona.PARK_SENIOR);

        assertThat(client.loans("access", USER_SEQ_NO, "020", null).loans()).isEmpty();
    }

    @Test
    void should_returnNoTransactions_outsideTheRequestedPeriod() {
        String salaryAccount = accountsOf(Persona.KIM_KUKMIN).accounts().get(0).fintechUseNumber();

        TransactionPage page =
                client.transactions("access", salaryAccount, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20), null);

        assertThat(page.hasNextPage()).isFalse();
        assertThat(page.transactions())
                .isNotEmpty()
                .allSatisfy(transaction ->
                        assertThat(transaction.date()).isBetween(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20)));
    }

    @Test
    void should_rejectFintechUseNumber_outsideTheSampleAccounts() {
        assertThatThrownBy(() -> client.balance("access", "999999999999999999999999"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPEN_BANKING_ACCOUNT_NOT_FOUND));
    }

    private Account salaryAccount(Persona persona) {
        return accountsOf(persona).accounts().stream()
                .filter(account ->
                        transactions(account, MONTH_FROM, MONTH_TO).stream().anyMatch(this::isSalary))
                .findFirst()
                .orElseThrow();
    }

    private UserInfo accountsOf(Persona persona) {
        personaSelection.select(MEMBER_ID, persona);
        return client.userInfo("access", USER_SEQ_NO);
    }

    /** 페르소나의 모든 계좌에서 완결된 한 달치 거래를 모은다. 월 단위 불변식은 전부 이 목록 위에서 본다. */
    private List<Transaction> monthTransactions(Persona persona) {
        return accountsOf(persona).accounts().stream()
                .flatMap(account -> transactions(account, MONTH_FROM, MONTH_TO).stream())
                .toList();
    }

    private List<Transaction> transactions(Account account, LocalDate fromDate, LocalDate toDate) {
        return client.transactions("access", account.fintechUseNumber(), fromDate, toDate, null)
                .transactions();
    }

    private BigDecimal totalBalance(UserInfo userInfo) {
        return userInfo.accounts().stream()
                .map(account -> client.balance("access", account.fintechUseNumber()))
                .map(Balance::balanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isSalary(Transaction transaction) {
        return MockPersonaFixtures.TYPE_SALARY.equals(transaction.type());
    }

    /** 소비 출금 = 출금 가운데 본인 계좌 간 이체도 대출상환도 아닌 것. 페르소나는 그 셋을 따로 들고 있다. */
    private boolean isExpense(Transaction transaction) {
        return "출금".equals(transaction.direction())
                && !MockPersonaFixtures.TYPE_INTERNAL_TRANSFER.equals(transaction.type())
                && !MockPersonaFixtures.TYPE_DEBT_REPAYMENT.equals(transaction.type());
    }

    private BigDecimal signedAmount(Transaction transaction) {
        return "입금".equals(transaction.direction())
                ? transaction.amount()
                : transaction.amount().negate();
    }

    private BigDecimal won(long value) {
        return BigDecimal.valueOf(value);
    }
}
