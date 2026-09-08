package com.homerun.global.external.openbanking;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.global.external.openbanking.OpenBankingResponses.Transaction;
import com.homerun.global.external.openbanking.OpenBankingResponses.TransactionPage;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MockOpenBankingClientTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 9, 8).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));

    private final MockOpenBankingClient client = new MockOpenBankingClient(
            new OpenBankingProperties(
                    "https://mock", "https://mock", "", "", "", "http://localhost:8080/callback", "login inquiry", ""),
            new ObjectMapper(),
            CLOCK);

    @Test
    @DisplayName("급여계좌는 조회기간의 달마다 급여 입금이 한 건씩 잡힌다")
    void should_returnOneSalaryPerMonth_when_salaryAccount() {
        TransactionPage page = client.transactions(
                "token", "199001010000000000000001", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31), null);

        assertThat(page.transactions()).hasSize(3);
        assertThat(page.transactions())
                .extracting(Transaction::date)
                .containsExactly(LocalDate.of(2026, 6, 25), LocalDate.of(2026, 7, 25), LocalDate.of(2026, 8, 25));
        // 이 두 값이 아니면 소득 집계(OpenBankingFinancialSummaryAggregator)가 급여로 세지 않는다.
        assertThat(page.transactions()).allSatisfy(transaction -> {
            assertThat(transaction.direction()).isEqualTo("입금");
            assertThat(transaction.type()).isEqualTo("급여");
        });
    }

    @Test
    @DisplayName("급여계좌가 아니면 급여가 잡히지 않는다 — 두 계좌에 넣으면 소득이 두 배가 된다")
    void should_returnNoSalary_when_otherAccount() {
        TransactionPage page = client.transactions(
                "token", "199001010000000000000002", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31), null);

        assertThat(page.transactions()).isEmpty();
    }

    @Test
    @DisplayName("인가 URL 은 금융결제원 대신 우리 콜백으로 곧장 돌아온다")
    void should_redirectToOwnCallback_when_authorizationUri() {
        assertThat(client.authorizationUri("state-1").toString())
                .isEqualTo("http://localhost:8080/callback?code=mock-authorization-code&state=state-1");
    }
}
