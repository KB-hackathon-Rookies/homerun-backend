package com.homerun.global.external.openbanking;

import com.homerun.global.external.openbanking.OpenBankingResponses.Balance;
import com.homerun.global.external.openbanking.OpenBankingResponses.LoanBasicPage;
import com.homerun.global.external.openbanking.OpenBankingResponses.LoanPage;
import com.homerun.global.external.openbanking.OpenBankingResponses.Token;
import com.homerun.global.external.openbanking.OpenBankingResponses.TransactionPage;
import com.homerun.global.external.openbanking.OpenBankingResponses.UserInfo;
import java.net.URI;
import java.time.LocalDate;

/**
 * 금융결제원 오픈뱅킹 업스트림 경계.
 *
 * <p>구현은 두 가지다. {@link HttpOpenBankingClient} 가 실제 호출을 담당하고,
 * {@code external-api.open-banking.mock-data=true} 일 때만 {@link MockDataOpenBankingClient} 가
 * 이 자리를 대신한다. 서비스는 이 인터페이스만 알기 때문에 계좌 목록·잔액·거래내역·대출·
 * financial-summary 집계·1루 open-banking-sync 가 한 경계에서 함께 덮인다.
 */
public interface OpenBankingClient {

    URI authorizationUri(String state);

    Token exchangeAuthorizationCode(String code);

    Token refreshToken(String refreshToken);

    UserInfo userInfo(String accessToken, String userSeqNo);

    Balance balance(String accessToken, String fintechUseNumber);

    TransactionPage transactions(
            String accessToken,
            String fintechUseNumber,
            LocalDate fromDate,
            LocalDate toDate,
            String beforeInquiryTraceInfo);

    LoanPage loans(String accessToken, String userSeqNo, String bankCode, String beforeInquiryTraceInfo);

    LoanBasicPage loanBasic(
            String accessToken,
            String userSeqNo,
            OpenBankingResponses.Loan loan,
            LocalDate fromDate,
            LocalDate toDate,
            String beforeInquiryTraceInfo);
}
