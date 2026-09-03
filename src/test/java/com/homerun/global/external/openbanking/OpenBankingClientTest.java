package com.homerun.global.external.openbanking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.homerun.global.exception.BusinessException;
import com.homerun.global.external.resilience.ExternalApiResilienceProperties;
import com.homerun.global.external.resilience.ExternalApiRetryExecutor;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class OpenBankingClientTest {

    private MockRestServiceServer server;
    private OpenBankingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenBankingClient(
                new OpenBankingProperties(
                        "https://oauth.example.com",
                        "https://api.example.com",
                        "client-id",
                        "client-secret",
                        "1234567890",
                        "https://app.example.com/callback",
                        "login inquiry",
                        "unused"),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-03T01:02:03Z"), ZoneOffset.UTC),
                builder.build(),
                new ExternalApiRetryExecutor(new ExternalApiResilienceProperties(
                        Duration.ofSeconds(1), Duration.ofSeconds(1), 2, Duration.ZERO)));
    }

    @Test
    void should_buildAuthorizationUri_withRequiredOAuthParameters() {
        URI uri = client.authorizationUri("state-value");

        assertThat(uri.toString())
                .startsWith("https://oauth.example.com/oauth/2.0/authorize?")
                .contains("response_type=code")
                .contains("client_id=client-id")
                .contains("redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback")
                .contains("scope=login%20inquiry")
                .contains("state=state-value")
                .contains("auth_type=0");
    }

    @Test
    void should_exchangeAuthorizationCode() {
        server.expect(requestTo("https://oauth.example.com/oauth/2.0/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "access_token":"access",
                          "refresh_token":"refresh",
                          "token_type":"Bearer",
                          "scope":"login inquiry",
                          "user_seq_no":"1100000000",
                          "expires_in":"7776000",
                          "refresh_token_expires_in":"31536000"
                        }
                        """, MediaType.APPLICATION_JSON));

        OpenBankingResponses.Token token = client.exchangeAuthorizationCode("authorization-code");

        assertThat(token.accessToken()).isEqualTo("access");
        assertThat(token.userSeqNo()).isEqualTo("1100000000");
        assertThat(token.expiresInSeconds()).isEqualTo(7_776_000L);
        server.verify();
    }

    @Test
    void should_parseRegisteredAccounts() {
        server.expect(requestTo(containsString("https://api.example.com/v2.0/user/me?user_seq_no=1100000000")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "rsp_code":"A0000",
                          "user_seq_no":"1100000000",
                          "user_name":"홍길동",
                          "res_list":[{
                            "account_alias":"급여통장",
                            "bank_code_std":"004",
                            "bank_name":"국민은행",
                            "fintech_use_num":"123456789012345678901234",
                            "account_num_masked":"123-***-456789",
                            "account_holder_name":"홍길동",
                            "account_type":"1"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        OpenBankingResponses.UserInfo response = client.userInfo("access", "1100000000");

        assertThat(response.accounts()).singleElement().satisfies(account -> {
            assertThat(account.bankName()).isEqualTo("국민은행");
            assertThat(account.fintechUseNumber()).isEqualTo("123456789012345678901234");
        });
        server.verify();
    }

    @Test
    void should_parseBalanceAndTransactionPage() {
        server.expect(requestTo(containsString("/v2.0/account/balance/fin_num?")))
                .andRespond(withSuccess("""
                        {
                          "rsp_code":"A0000",
                          "bank_name":"국민은행",
                          "fintech_use_num":"123456789012345678901234",
                          "balance_amt":"18000000",
                          "available_amt":"17500000",
                          "account_type":"1",
                          "product_name":"급여통장",
                          "account_issue_date":"20200102",
                          "last_tran_date":"20260902"
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/v2.0/account/transaction_list/fin_num?")))
                .andRespond(withSuccess("""
                        {
                          "rsp_code":"A0000",
                          "bank_name":"국민은행",
                          "fintech_use_num":"123456789012345678901234",
                          "balance_amt":"18000000",
                          "next_page_yn":"Y",
                          "befor_inquiry_trace_info":"next-1",
                          "res_list":[{
                            "tran_date":"20260825",
                            "tran_time":"091500",
                            "inout_type":"입금",
                            "tran_type":"급여",
                            "print_content":"홈런컴퍼니",
                            "tran_amt":"2850000",
                            "after_balance_amt":"18000000",
                            "branch_name":"강남점"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));
        OpenBankingResponses.Balance balance = client.balance("access", "123456789012345678901234");
        OpenBankingResponses.TransactionPage page = client.transactions(
                "access", "123456789012345678901234", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null);

        assertThat(balance.balanceAmount()).isEqualByComparingTo("18000000");
        assertThat(balance.accountIssueDate()).isEqualTo(LocalDate.of(2020, 1, 2));
        assertThat(page.hasNextPage()).isTrue();
        assertThat(page.transactions()).singleElement().satisfies(transaction -> {
            assertThat(transaction.type()).isEqualTo("급여");
            assertThat(transaction.amount()).isEqualByComparingTo("2850000");
        });
        server.verify();
    }

    @Test
    void should_parseLoanListAndRepaymentTransactions() {
        server.expect(requestTo(containsString("/v2.0/loans?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "rsp_code":"A0000",
                          "bank_code_std":"004",
                          "bank_name":"국민은행",
                          "next_page_yn":"N",
                          "loan_list":[{
                            "account_num":"1234567890",
                            "account_seq":"001",
                            "account_num_masked":"123-***-890",
                            "prod_name":"직장인신용대출",
                            "account_type":"3100",
                            "account_status":"01"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(containsString("https://api.example.com/v2.0/loans/basic?")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(requestTo(containsString("account_num=1234567890")))
                .andExpect(requestTo(containsString("from_date=20260601")))
                .andRespond(withSuccess("""
                        {
                          "rsp_code":"A0000",
                          "repay_date":"20260825",
                          "repay_method":"01",
                          "repay_org_code":"004",
                          "next_repay_date":"20260925",
                          "next_page_yn":"N",
                          "res_list":[{
                            "trans_date":"20260825",
                            "trans_time":"090000",
                            "trans_type":"02",
                            "trans_amt":"-450000"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        OpenBankingResponses.LoanPage loanPage = client.loans("access", "1100000000", "004", null);
        OpenBankingResponses.Loan loan = loanPage.loans().get(0);
        OpenBankingResponses.LoanBasicPage basicPage = client.loanBasic(
                "access", "1100000000", loan, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31), null);

        assertThat(loan.bankName()).isEqualTo("국민은행");
        assertThat(loan.accountType()).isEqualTo("3100");
        assertThat(basicPage.nextRepaymentDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(basicPage.transactions()).singleElement().satisfies(transaction -> {
            assertThat(transaction.type()).isEqualTo("02");
            assertThat(transaction.amount()).isEqualByComparingTo("-450000");
        });
        server.verify();
    }

    @Test
    void should_retryLookupRequestAfterServerError() {
        server.expect(requestTo(containsString("/v2.0/account/balance/fin_num?")))
                .andRespond(withServerError());
        server.expect(requestTo(containsString("/v2.0/account/balance/fin_num?")))
                .andRespond(withSuccess("""
                        {
                          "rsp_code":"A0000",
                          "balance_amt":"1000",
                          "available_amt":"900"
                        }
                        """, MediaType.APPLICATION_JSON));

        OpenBankingResponses.Balance response = client.balance("access", "123456789012345678901234");

        assertThat(response.balanceAmount()).isEqualByComparingTo("1000");
        server.verify();
    }

    @Test
    void should_notRetryOAuthTokenRequest() {
        server.expect(requestTo("https://oauth.example.com/oauth/2.0/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.exchangeAuthorizationCode("authorization-code"))
                .isInstanceOf(BusinessException.class);
        server.verify();
    }
}
