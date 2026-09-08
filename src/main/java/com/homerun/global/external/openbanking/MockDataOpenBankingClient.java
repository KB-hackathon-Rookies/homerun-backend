package com.homerun.global.external.openbanking;

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
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 오픈뱅킹 <b>데이터 조회</b>만 샘플 픽스처로 바꿔치기하는 구현.
 *
 * <p>존재 이유는 하나다. 금융결제원 오픈뱅킹의 계좌·거래 조회 API(계좌 목록, 잔액, 거래내역, 대출)는
 * <b>사업자 등록이 끝난 이용기관만</b> 호출할 수 있는데, 데모 시점에는 등록이 아직 없다. 인가(OAuth)는
 * 사업자 없이도 실제로 동작하므로 {@link #authorizationUri}, {@link #exchangeAuthorizationCode},
 * {@link #refreshToken} 은 손대지 않고 {@link HttpOpenBankingClient} 로 그대로 위임한다. 즉 이 클래스가
 * 대신하는 것은 업스트림 데이터 조회일 뿐, 인가나 연결 상태 검증이 아니다. 연결이 없는 회원은 이 구현이
 * 켜져 있어도 서비스 계층에서 지금과 똑같이 실패한다.
 *
 * <p>{@code external-api.open-banking.mock-data=true}(env {@code OPEN_BANKING_MOCK_DATA}) 일 때만
 * 빈으로 올라간다. 기본값은 false 이므로 운영·CI 동작은 그대로다. 사업자 등록이 끝나면 이 클래스와
 * 플래그를 함께 지운다.
 *
 * <p>픽스처는 사회초년생 한 명의 일관된 프로필이다. 월 실수령 280만원이 급여통장으로 매달 들어오고,
 * 그중 80만원은 자유적금, 10만원은 주택청약으로 자동이체되며, 월세·카드 결제까지 빠져나가 급여통장
 * 잔액은 320만원에서 제자리를 유지한다. 세 계좌 잔액 합은 4,000만원이고, 전세자금대출 이자 15만원이
 * 매달 나간다. 어느 화면에서 봐도 숫자가 서로 어긋나지 않도록 모든 엔드포인트가 같은 표에서 값을 만든다.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "external-api.open-banking", name = "mock-data", havingValue = "true")
public class MockDataOpenBankingClient implements OpenBankingClient {

    private static final Logger log = LoggerFactory.getLogger(MockDataOpenBankingClient.class);

    private static final String BANK_CODE = "004";
    private static final String BANK_NAME = "국민은행";
    private static final String HOLDER_NAME = "홍길동";
    private static final String BRANCH_NAME = "샘플지점";

    private static final String LOAN_ACCOUNT_NUMBER = "SAMPLE-LOAN-0001";
    private static final String LOAN_ACCOUNT_MASKED = "004-**-****-3456";
    private static final BigDecimal LOAN_MONTHLY_REPAYMENT = amount(150_000);
    private static final int LOAN_REPAYMENT_DAY = 25;
    private static final String LOAN_REPAYMENT_TRANSACTION_TYPE = "02";

    /** 급여통장·자유적금·주택청약. 잔액 합계 4,000만원. */
    private static final List<MockAccount> ACCOUNTS = List.of(
            new MockAccount(
                    "SAMPLE-FIN-0001",
                    "샘플 급여통장",
                    "004-**-****-1234",
                    "1",
                    "샘플 직장인 우대통장",
                    amount(3_200_000),
                    List.of(
                            entry(10, LocalTime.of(12, 30), Direction.WITHDRAWAL, "카드", "샘플 생활비 체크카드", 400_000),
                            entry(25, LocalTime.of(9, 10), Direction.DEPOSIT, "급여", "샘플급여", 2_800_000),
                            entry(26, LocalTime.of(6, 0), Direction.WITHDRAWAL, "이체", "샘플 자유적금 자동이체", 800_000),
                            entry(26, LocalTime.of(6, 5), Direction.WITHDRAWAL, "이체", "샘플 주택청약 자동이체", 100_000),
                            entry(27, LocalTime.of(6, 0), Direction.WITHDRAWAL, "이체", "샘플 월세", 700_000),
                            entry(28, LocalTime.of(13, 0), Direction.WITHDRAWAL, "카드", "샘플 신용카드 결제", 800_000))),
            new MockAccount(
                    "SAMPLE-FIN-0002",
                    "샘플 자유적금",
                    "004-**-****-5678",
                    "2",
                    "샘플 자유적금",
                    amount(24_800_000),
                    List.of(entry(26, LocalTime.of(6, 0), Direction.DEPOSIT, "이체", "샘플 자유적금 납입", 800_000))),
            new MockAccount(
                    "SAMPLE-FIN-0003",
                    "샘플 주택청약저축",
                    "004-**-****-9012",
                    "2",
                    "샘플 주택청약종합저축",
                    amount(12_000_000),
                    List.of(entry(26, LocalTime.of(6, 5), Direction.DEPOSIT, "이체", "샘플 주택청약 납입", 100_000))));

    private final HttpOpenBankingClient authorizationDelegate;
    private final Clock clock;

    public MockDataOpenBankingClient(HttpOpenBankingClient authorizationDelegate, Clock clock) {
        this.authorizationDelegate = authorizationDelegate;
        this.clock = clock;
        log.warn("오픈뱅킹 데이터 조회가 샘플 픽스처로 대체됩니다. 인가(OAuth)는 실제 호출 그대로입니다.");
    }

    @Override
    public URI authorizationUri(String state) {
        return authorizationDelegate.authorizationUri(state);
    }

    @Override
    public Token exchangeAuthorizationCode(String code) {
        return authorizationDelegate.exchangeAuthorizationCode(code);
    }

    @Override
    public Token refreshToken(String refreshToken) {
        return authorizationDelegate.refreshToken(refreshToken);
    }

    @Override
    public UserInfo userInfo(String accessToken, String userSeqNo) {
        List<Account> accounts = ACCOUNTS.stream()
                .map(account -> new Account(
                        account.alias(),
                        BANK_CODE,
                        BANK_NAME,
                        "",
                        account.fintechUseNumber(),
                        account.accountNumberMasked(),
                        HOLDER_NAME,
                        account.accountType()))
                .toList();
        return new UserInfo(userSeqNo, HOLDER_NAME, accounts);
    }

    @Override
    public Balance balance(String accessToken, String fintechUseNumber) {
        MockAccount account = requireAccount(fintechUseNumber);
        LocalDate today = LocalDate.now(clock);
        return new Balance(
                BANK_NAME,
                "",
                account.fintechUseNumber(),
                account.balance(),
                account.balance(),
                account.accountType(),
                account.productName(),
                today.minusYears(3).withDayOfMonth(1),
                null,
                lastTransactionDate(account, today),
                Instant.now(clock));
    }

    @Override
    public TransactionPage transactions(
            String accessToken,
            String fintechUseNumber,
            LocalDate fromDate,
            LocalDate toDate,
            String beforeInquiryTraceInfo) {
        MockAccount account = requireAccount(fintechUseNumber);
        return new TransactionPage(
                BANK_NAME,
                "",
                account.fintechUseNumber(),
                account.balance(),
                false,
                "",
                transactionsBetween(account, fromDate, toDate),
                Instant.now(clock));
    }

    @Override
    public LoanPage loans(String accessToken, String userSeqNo, String bankCode, String beforeInquiryTraceInfo) {
        List<Loan> loans = BANK_CODE.equals(bankCode)
                ? List.of(new Loan(
                        BANK_CODE,
                        BANK_NAME,
                        LOAN_ACCOUNT_NUMBER,
                        "",
                        LOAN_ACCOUNT_MASKED,
                        "샘플 청년전세자금대출",
                        "3170",
                        "01"))
                : List.of();
        return new LoanPage(false, "", loans, Instant.now(clock));
    }

    @Override
    public LoanBasicPage loanBasic(
            String accessToken,
            String userSeqNo,
            Loan loan,
            LocalDate fromDate,
            LocalDate toDate,
            String beforeInquiryTraceInfo) {
        List<LoanTransaction> repayments = new ArrayList<>();
        for (LocalDate date : monthlyDates(fromDate, toDate, LOAN_REPAYMENT_DAY)) {
            repayments.add(new LoanTransaction(
                    date, LocalTime.of(6, 0), LOAN_REPAYMENT_TRANSACTION_TYPE, LOAN_MONTHLY_REPAYMENT));
        }
        return new LoanBasicPage(
                String.valueOf(LOAN_REPAYMENT_DAY),
                "01",
                BANK_CODE,
                nextRepaymentDate(),
                false,
                "",
                List.copyOf(repayments),
                Instant.now(clock));
    }

    /**
     * 최신 거래가 항상 잔액 조회 값에서 끝나도록 뒤에서부터 잔액을 되짚어 만든다. 조회 기간을 어떻게 잡아도
     * 거래내역의 잔액과 잔액 조회 결과가 어긋나지 않는다.
     */
    private List<Transaction> transactionsBetween(MockAccount account, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            return List.of();
        }
        List<Occurrence> occurrences = new ArrayList<>();
        for (YearMonth month = YearMonth.from(fromDate);
                !month.isAfter(YearMonth.from(toDate));
                month = month.plusMonths(1)) {
            for (MonthlyEntry entry : account.monthlyEntries()) {
                LocalDate date = month.atDay(entry.dayOfMonth());
                if (!date.isBefore(fromDate) && !date.isAfter(toDate)) {
                    occurrences.add(new Occurrence(date, entry));
                }
            }
        }
        occurrences.sort((left, right) -> {
            int byDate = right.date().compareTo(left.date());
            return byDate != 0
                    ? byDate
                    : right.entry().time().compareTo(left.entry().time());
        });

        List<Transaction> transactions = new ArrayList<>();
        BigDecimal balanceAfter = account.balance();
        for (Occurrence occurrence : occurrences) {
            MonthlyEntry entry = occurrence.entry();
            transactions.add(new Transaction(
                    occurrence.date(),
                    entry.time(),
                    entry.direction().label(),
                    entry.type(),
                    entry.description(),
                    entry.amount(),
                    balanceAfter,
                    BRANCH_NAME));
            balanceAfter = balanceAfter.subtract(entry.signedAmount());
        }
        return List.copyOf(transactions);
    }

    private LocalDate lastTransactionDate(MockAccount account, LocalDate today) {
        return transactionsBetween(account, today.minusMonths(2).withDayOfMonth(1), today).stream()
                .findFirst()
                .map(Transaction::date)
                .orElse(null);
    }

    private List<LocalDate> monthlyDates(LocalDate fromDate, LocalDate toDate, int dayOfMonth) {
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            return List.of();
        }
        List<LocalDate> dates = new ArrayList<>();
        for (YearMonth month = YearMonth.from(fromDate);
                !month.isAfter(YearMonth.from(toDate));
                month = month.plusMonths(1)) {
            LocalDate date = month.atDay(dayOfMonth);
            if (!date.isBefore(fromDate) && !date.isAfter(toDate)) {
                dates.add(date);
            }
        }
        return List.copyOf(dates);
    }

    private LocalDate nextRepaymentDate() {
        LocalDate today = LocalDate.now(clock);
        LocalDate thisMonth = today.withDayOfMonth(LOAN_REPAYMENT_DAY);
        return thisMonth.isAfter(today) ? thisMonth : thisMonth.plusMonths(1);
    }

    private MockAccount requireAccount(String fintechUseNumber) {
        return ACCOUNTS.stream()
                .filter(account -> account.fintechUseNumber().equals(fintechUseNumber))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.OPEN_BANKING_ACCOUNT_NOT_FOUND));
    }

    private static MonthlyEntry entry(
            int dayOfMonth, LocalTime time, Direction direction, String type, String description, long amount) {
        return new MonthlyEntry(dayOfMonth, time, direction, type, description, amount(amount));
    }

    private static BigDecimal amount(long value) {
        return BigDecimal.valueOf(value);
    }

    private enum Direction {
        DEPOSIT("입금"),
        WITHDRAWAL("출금");

        private final String label;

        Direction(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    private record MonthlyEntry(
            int dayOfMonth, LocalTime time, Direction direction, String type, String description, BigDecimal amount) {

        private BigDecimal signedAmount() {
            return direction == Direction.DEPOSIT ? amount : amount.negate();
        }
    }

    private record MockAccount(
            String fintechUseNumber,
            String alias,
            String accountNumberMasked,
            String accountType,
            String productName,
            BigDecimal balance,
            List<MonthlyEntry> monthlyEntries) {}

    private record Occurrence(LocalDate date, MonthlyEntry entry) {}
}
