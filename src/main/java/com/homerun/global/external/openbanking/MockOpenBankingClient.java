package com.homerun.global.external.openbanking;

import com.homerun.global.external.openbanking.OpenBankingResponses.Account;
import com.homerun.global.external.openbanking.OpenBankingResponses.Balance;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * OPEN_BANKING_CLIENT_ID 가 없을 때 쓰는 목 오픈뱅킹(DIA-01-00).
 *
 * <p>인가는 금융결제원 화면 대신 우리 콜백으로 곧장 되돌린다. 새 창이 {@code redirect-uri} 를 열면
 * 세션에 담긴 state 가 그대로 맞아 연결이 성립한다.
 *
 * <p>급여는 {@code 입금 + 급여} 인 거래만 소득으로 잡힌다. 두 계좌 모두에 급여를 넣으면 소득이 두 배로
 * 잡히므로 주거래 계좌 하나에만 넣는다.
 */
@Component
@Primary
@ConditionalOnExpression("'${external-api.open-banking.client-id:}'.isBlank()")
public class MockOpenBankingClient extends OpenBankingClient {

    private static final String SALARY_ACCOUNT = "199001010000000000000001";
    private static final BigDecimal MONTHLY_SALARY = new BigDecimal("2800000");

    private final OpenBankingProperties properties;
    private final Clock clock;

    public MockOpenBankingClient(OpenBankingProperties properties, ObjectMapper objectMapper, Clock clock) {
        super(properties, objectMapper, clock, null);
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public URI authorizationUri(String state) {
        return URI.create(properties.redirectUri() + "?code=mock-authorization-code&state=" + state);
    }

    @Override
    public Token exchangeAuthorizationCode(String code) {
        return mockToken();
    }

    @Override
    public Token refreshToken(String refreshToken) {
        return mockToken();
    }

    @Override
    public UserInfo userInfo(String accessToken, String userSeqNo) {
        return new UserInfo(
                "1100000001",
                "홍길동",
                List.of(
                        new Account("월급통장", "004", "국민은행", null, SALARY_ACCOUNT, "123-45-****89", "홍길동", "1"),
                        new Account(
                                "청약저축",
                                "088",
                                "신한은행",
                                null,
                                "199001010000000000000002",
                                "110-***-****12",
                                "홍길동",
                                "2")));
    }

    @Override
    public Balance balance(String accessToken, String fintechUseNumber) {
        BigDecimal amount =
                SALARY_ACCOUNT.equals(fintechUseNumber) ? new BigDecimal("4320000") : new BigDecimal("12000000");
        return new Balance(
                SALARY_ACCOUNT.equals(fintechUseNumber) ? "국민은행" : "신한은행",
                null,
                fintechUseNumber,
                amount,
                amount,
                "1",
                SALARY_ACCOUNT.equals(fintechUseNumber) ? "KB국민ONE통장" : "주택청약종합저축",
                LocalDate.of(2023, 3, 2),
                null,
                LocalDate.now(clock),
                Instant.now(clock));
    }

    @Override
    public TransactionPage transactions(
            String accessToken,
            String fintechUseNumber,
            LocalDate fromDate,
            LocalDate toDate,
            String beforeInquiryTraceInfo) {
        List<Transaction> transactions = new ArrayList<>();
        if (SALARY_ACCOUNT.equals(fintechUseNumber)) {
            for (LocalDate payday = payday(fromDate); !payday.isAfter(toDate); payday = payday.plusMonths(1)) {
                transactions.add(new Transaction(
                        payday,
                        LocalTime.of(9, 30),
                        "입금",
                        "급여",
                        "(주)루키즈 급여",
                        MONTHLY_SALARY,
                        new BigDecimal("4320000"),
                        "본점"));
            }
        }
        return new TransactionPage(
                SALARY_ACCOUNT.equals(fintechUseNumber) ? "국민은행" : "신한은행",
                null,
                fintechUseNumber,
                new BigDecimal("4320000"),
                false,
                null,
                List.copyOf(transactions),
                Instant.now(clock));
    }

    @Override
    public LoanPage loans(String accessToken, String userSeqNo, String bankCode, String beforeInquiryTraceInfo) {
        return new LoanPage(false, null, List.of(), Instant.now(clock));
    }

    /** 급여일은 25일. 조회 시작일이 25일을 지났으면 다음 달부터 센다. */
    private LocalDate payday(LocalDate fromDate) {
        LocalDate candidate = fromDate.withDayOfMonth(25);
        return candidate.isBefore(fromDate) ? candidate.plusMonths(1) : candidate;
    }

    private Token mockToken() {
        return new Token(
                "mock-access-token",
                "mock-refresh-token",
                "Bearer",
                properties.scope(),
                "1100000001",
                7_776_000L,
                7_776_000L);
    }
}
