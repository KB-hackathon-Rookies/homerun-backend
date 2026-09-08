package com.homerun.global.external.openbanking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.openbanking.OpenBankingResponses.Account;
import com.homerun.global.external.openbanking.OpenBankingResponses.Balance;
import com.homerun.global.external.openbanking.OpenBankingResponses.Loan;
import com.homerun.global.external.openbanking.OpenBankingResponses.LoanBasicPage;
import com.homerun.global.external.openbanking.OpenBankingResponses.LoanPage;
import com.homerun.global.external.openbanking.OpenBankingResponses.LoanTransaction;
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
import org.junit.jupiter.api.Test;

/** 사업자 등록 전 데모용 샘플 픽스처가 한 사람의 일관된 재무 프로필로 읽히는지 확인한다. */
class MockDataOpenBankingClientTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SALARY_ACCOUNT = "SAMPLE-FIN-0001";
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 8, 31);

    private HttpOpenBankingClient authorizationDelegate;
    private MockDataOpenBankingClient client;

    @BeforeEach
    void setUp() {
        authorizationDelegate = mock(HttpOpenBankingClient.class);
        client = new MockDataOpenBankingClient(authorizationDelegate, CLOCK);
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
    void should_returnAccounts_thatLookObviouslyLikeSampleData() {
        UserInfo userInfo = client.userInfo("access", "1100000000");

        assertThat(userInfo.userSeqNo()).isEqualTo("1100000000");
        assertThat(userInfo.userName()).isEqualTo("홍길동");
        assertThat(userInfo.accounts()).hasSize(3).allSatisfy(account -> {
            assertThat(account.accountAlias()).contains("샘플");
            assertThat(account.accountHolderName()).isEqualTo("홍길동");
            assertThat(account.accountNumberMasked()).contains("*");
            assertThat(account.fintechUseNumber()).startsWith("SAMPLE-");
        });
    }

    @Test
    void should_sumAccountBalances_toFortyMillion() {
        BigDecimal total = client.userInfo("access", "1100000000").accounts().stream()
                .map(Account::fintechUseNumber)
                .map(fintechUseNumber -> client.balance("access", fintechUseNumber))
                .map(Balance::balanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(total).isEqualByComparingTo("40000000");
    }

    @Test
    void should_payMonthlySalaryOfTwoMillionEightHundredThousand_intoTheSalaryAccount() {
        List<Transaction> salaries =
                client.transactions("access", SALARY_ACCOUNT, FROM_DATE, TO_DATE, null).transactions().stream()
                        .filter(transaction -> "급여".equals(transaction.type()))
                        .toList();

        assertThat(salaries).hasSize(3).allSatisfy(salary -> {
            assertThat(salary.direction()).isEqualTo("입금");
            assertThat(salary.amount()).isEqualByComparingTo("2800000");
        });
        assertThat(salaries)
                .extracting(Transaction::date)
                .containsExactly(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 7, 25), LocalDate.of(2026, 6, 25));
    }

    @Test
    void should_payTheSalaryIntoExactlyOneAccount_soMonthlyIncomeIsNotDoubleCounted() {
        long accountsWithSalary = client.userInfo("access", "1100000000").accounts().stream()
                .map(Account::fintechUseNumber)
                .map(fintechUseNumber -> client.transactions("access", fintechUseNumber, FROM_DATE, TO_DATE, null))
                .filter(page -> page.transactions().stream().anyMatch(transaction -> "급여".equals(transaction.type())))
                .count();

        assertThat(accountsWithSalary).isEqualTo(1);
    }

    @Test
    void should_endTransactionSeries_atTheBalanceReportedByBalanceLookup() {
        for (Account account : client.userInfo("access", "1100000000").accounts()) {
            TransactionPage page = client.transactions("access", account.fintechUseNumber(), FROM_DATE, TO_DATE, null);
            Balance balance = client.balance("access", account.fintechUseNumber());

            assertThat(page.currentBalance()).isEqualByComparingTo(balance.balanceAmount());
            assertThat(page.transactions().get(0).balanceAfterTransaction())
                    .isEqualByComparingTo(balance.balanceAmount());
        }
    }

    @Test
    void should_keepSalaryAccountBalanceFlat_becauseMonthlyInflowMatchesOutflow() {
        List<Transaction> transactions = client.transactions("access", SALARY_ACCOUNT, FROM_DATE, TO_DATE, null)
                .transactions();

        BigDecimal monthlyNet = transactions.stream()
                .filter(transaction -> transaction.date().getMonthValue() == 8)
                .map(transaction -> "입금".equals(transaction.direction())
                        ? transaction.amount()
                        : transaction.amount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(monthlyNet).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transactions.stream()
                        .filter(transaction -> transaction.date().getDayOfMonth() == 28)
                        .map(Transaction::balanceAfterTransaction)
                        .toList())
                .hasSize(3)
                .allSatisfy(balance -> assertThat(balance).isEqualByComparingTo("3200000"));
    }

    @Test
    void should_returnNoTransactions_outsideTheRequestedPeriod() {
        TransactionPage page = client.transactions(
                "access", SALARY_ACCOUNT, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20), null);

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

    @Test
    void should_returnSingleLoan_onlyForTheBankHoldingTheSampleAccounts() {
        LoanPage loans = client.loans("access", "1100000000", "004", null);
        LoanPage otherBank = client.loans("access", "1100000000", "020", null);

        assertThat(otherBank.loans()).isEmpty();
        assertThat(loans.hasNextPage()).isFalse();
        assertThat(loans.loans()).singleElement().satisfies(loan -> {
            assertThat(loan.productName()).contains("샘플");
            assertThat(loan.accountType()).isEqualTo("3170");
            assertThat(loan.accountNumber()).isNotBlank();
        });
    }

    @Test
    void should_repayOneHundredFiftyThousandOfLoanInterest_everyMonth() {
        Loan loan = client.loans("access", "1100000000", "004", null).loans().get(0);

        LoanBasicPage page = client.loanBasic("access", "1100000000", loan, FROM_DATE, TO_DATE, null);

        assertThat(page.hasNextPage()).isFalse();
        assertThat(page.transactions()).hasSize(3).allSatisfy(repayment -> {
            assertThat(repayment.type()).isEqualTo("02");
            assertThat(repayment.amount()).isEqualByComparingTo("150000");
        });
        assertThat(page.transactions().stream().map(LoanTransaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("450000");
    }
}
