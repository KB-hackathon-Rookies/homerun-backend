package com.homerun.global.external.openbanking;

import com.homerun.domain.openbanking.type.Persona;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.openbanking.MockPersonaFixtures.MockAccount;
import com.homerun.global.external.openbanking.MockPersonaFixtures.MockLoan;
import com.homerun.global.external.openbanking.MockPersonaFixtures.MockProfile;
import com.homerun.global.external.openbanking.MockPersonaFixtures.MonthlyEntry;
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
 * <p>픽스처는 {@link Persona} 한 명의 일관된 프로필이다. 어느 화면에서 봐도 숫자가 서로 어긋나지
 * 않도록 모든 엔드포인트가 {@link MockPersonaFixtures} 라는 같은 표에서 값을 만들고, 그 표의 헤드라인
 * 수치는 페르소나 적재 경로가 쓰는 {@link Persona} 와 같은 값이다. 한때 이 클래스는 페르소나와 무관한
 * 홍길동 한 명을 고정으로 답했고, 그래서 오픈뱅킹을 연결하면 4,000만원이 페르소나를 실으면 1,500만원이
 * 나오는 어긋남이 있었다.
 *
 * <p>어떤 페르소나로 답할지는 {@link MockPersonaSelection} 이 정한다. 기본은 김국민이다.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "external-api.open-banking", name = "mock-data", havingValue = "true")
public class MockDataOpenBankingClient implements OpenBankingClient {

    private static final Logger log = LoggerFactory.getLogger(MockDataOpenBankingClient.class);

    private static final String LOAN_REPAYMENT_TRANSACTION_TYPE = "02";

    private final HttpOpenBankingClient authorizationDelegate;
    private final Clock clock;
    private final MockPersonaSelection personaSelection;

    public MockDataOpenBankingClient(
            HttpOpenBankingClient authorizationDelegate, Clock clock, MockPersonaSelection personaSelection) {
        this.authorizationDelegate = authorizationDelegate;
        this.clock = clock;
        this.personaSelection = personaSelection;
        log.warn(
                "오픈뱅킹 데이터 조회가 샘플 픽스처로 대체됩니다. 기본 페르소나는 {} 입니다. 인가(OAuth)는 실제 호출 그대로입니다.",
                personaSelection.defaultPersona().getLabel());
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
        MockProfile profile = profile(userSeqNo);
        List<Account> accounts = profile.accounts().stream()
                .map(account -> new Account(
                        account.alias(),
                        MockPersonaFixtures.BANK_CODE,
                        MockPersonaFixtures.BANK_NAME,
                        "",
                        account.fintechUseNumber(),
                        account.accountNumberMasked(),
                        profile.holderName(),
                        account.accountType()))
                .toList();
        return new UserInfo(userSeqNo, profile.holderName(), accounts);
    }

    /**
     * 핀테크이용번호에 페르소나가 박혀 있어(예: {@code SAMPLE-KIM-0001}) 회원 식별자 없이도 어느
     * 프로필의 계좌인지 되짚을 수 있다. 계좌 소유권 검증은 서비스 계층이 계좌 목록으로 하므로,
     * 여기서 전 페르소나를 뒤져도 남의 페르소나 계좌를 조회할 수는 없다.
     */
    @Override
    public Balance balance(String accessToken, String fintechUseNumber) {
        MockAccount account = requireAccount(fintechUseNumber);
        LocalDate today = LocalDate.now(clock);
        return new Balance(
                MockPersonaFixtures.BANK_NAME,
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
                MockPersonaFixtures.BANK_NAME,
                "",
                account.fintechUseNumber(),
                account.balance(),
                false,
                "",
                transactionsBetween(account, fromDate, toDate),
                Instant.now(clock));
    }

    /** 대출이 없는 페르소나는 잔액 0짜리 대출이 아니라 <b>빈 목록</b>을 돌려준다. 0원 대출은 화면에 없는 부채를 그린다. */
    @Override
    public LoanPage loans(String accessToken, String userSeqNo, String bankCode, String beforeInquiryTraceInfo) {
        MockProfile profile = profile(userSeqNo);
        MockLoan loan = profile.loan();
        List<Loan> loans = loan == null || !MockPersonaFixtures.BANK_CODE.equals(bankCode)
                ? List.of()
                : List.of(new Loan(
                        MockPersonaFixtures.BANK_CODE,
                        MockPersonaFixtures.BANK_NAME,
                        loan.accountNumber(),
                        "",
                        loan.accountNumberMasked(),
                        loan.productName(),
                        "3170",
                        "01"));
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
        MockProfile profile = profileOfLoan(loan);
        BigDecimal monthlyRepayment = BigDecimal.valueOf(profile.persona().getMonthlyDebtPayment());
        int repaymentDay = profile.loan().repaymentDay();
        List<LoanTransaction> repayments = new ArrayList<>();
        for (LocalDate date : monthlyDates(fromDate, toDate, repaymentDay)) {
            repayments.add(
                    new LoanTransaction(date, LocalTime.of(6, 0), LOAN_REPAYMENT_TRANSACTION_TYPE, monthlyRepayment));
        }
        return new LoanBasicPage(
                String.valueOf(repaymentDay),
                "01",
                MockPersonaFixtures.BANK_CODE,
                nextRepaymentDate(repaymentDay),
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
            YearMonth month = YearMonth.from(occurrence.date());
            transactions.add(new Transaction(
                    occurrence.date(),
                    entry.time(),
                    entry.direction().label(),
                    entry.type(),
                    entry.description(),
                    entry.amountAt(month),
                    balanceAfter,
                    MockPersonaFixtures.BRANCH_NAME));
            balanceAfter = balanceAfter.subtract(entry.signedAmountAt(month));
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

    private LocalDate nextRepaymentDate(int repaymentDay) {
        LocalDate today = LocalDate.now(clock);
        LocalDate thisMonth = today.withDayOfMonth(repaymentDay);
        return thisMonth.isAfter(today) ? thisMonth : thisMonth.plusMonths(1);
    }

    private MockProfile profile(String userSeqNo) {
        return MockPersonaFixtures.profile(personaSelection.resolve(userSeqNo));
    }

    private MockProfile profileOfLoan(Loan loan) {
        return MockPersonaFixtures.allProfiles().stream()
                .filter(profile -> profile.loan() != null)
                .filter(profile -> profile.loan().accountNumber().equals(loan == null ? null : loan.accountNumber()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.OPEN_BANKING_ACCOUNT_NOT_FOUND));
    }

    private MockAccount requireAccount(String fintechUseNumber) {
        return MockPersonaFixtures.allProfiles().stream()
                .flatMap(profile -> profile.accounts().stream())
                .filter(account -> account.fintechUseNumber().equals(fintechUseNumber))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.OPEN_BANKING_ACCOUNT_NOT_FOUND));
    }

    private record Occurrence(LocalDate date, MonthlyEntry entry) {}
}
