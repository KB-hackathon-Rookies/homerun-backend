package com.homerun.domain.openbanking.service;

import com.homerun.domain.openbanking.config.OpenBankingTokenCipher;
import com.homerun.domain.openbanking.dto.response.OpenBankingAccountResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingBalanceResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingConnectionResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingFinancialSummaryResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingLoanListResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingLoanResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingTransactionPageResponse;
import com.homerun.domain.openbanking.entity.OpenBankingConnection;
import com.homerun.domain.openbanking.repository.OpenBankingConnectionRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.openbanking.MockPersonaSelection;
import com.homerun.global.external.openbanking.OpenBankingClient;
import com.homerun.global.external.openbanking.OpenBankingResponses.Account;
import com.homerun.global.external.openbanking.OpenBankingResponses.Loan;
import com.homerun.global.external.openbanking.OpenBankingResponses.LoanPage;
import com.homerun.global.external.openbanking.OpenBankingResponses.Token;
import com.homerun.global.external.openbanking.OpenBankingResponses.UserInfo;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenBankingService {

    private static final long REFRESH_SAFETY_SECONDS = 30;
    private static final int MAX_PAGES = 100;

    private final OpenBankingConnectionRepository connectionRepository;
    private final OpenBankingClient client;
    private final OpenBankingTokenCipher tokenCipher;
    private final Clock clock;

    public OpenBankingService(
            OpenBankingConnectionRepository connectionRepository,
            OpenBankingClient client,
            OpenBankingTokenCipher tokenCipher,
            Clock clock) {
        this.connectionRepository = connectionRepository;
        this.client = client;
        this.tokenCipher = tokenCipher;
        this.clock = clock;
    }

    public URI authorizationUri(String state) {
        return client.authorizationUri(state);
    }

    @Transactional
    public OpenBankingConnectionResponse connect(Long memberId, String authorizationCode) {
        Token token = client.exchangeAuthorizationCode(authorizationCode);
        validateInitialToken(token);
        Instant now = Instant.now(clock);
        OpenBankingConnection connection =
                connectionRepository.findByMemberId(memberId).orElseGet(() -> OpenBankingConnection.create(memberId));
        connection.updateCredentials(
                token.userSeqNo(),
                tokenCipher.encrypt(memberId, token.accessToken()),
                tokenCipher.encrypt(memberId, token.refreshToken()),
                token.tokenType(),
                token.scope(),
                now.plusSeconds(token.expiresInSeconds()),
                expiresAt(now, token.refreshTokenExpiresInSeconds()),
                now);
        return OpenBankingConnectionResponse.from(connectionRepository.save(connection));
    }

    /**
     * 데모 전용 — 금융결제원 인가 없이 연결을 세운다.
     *
     * <p>실연동(사업자 등록)이 불가한 데모에서 인가 팝업·콜백 없이 연결 레코드만 만든다. 이후
     * {@code accounts}·{@code balance}·{@code financial-summary} 는 {@code MockDataOpenBankingClient}
     * 의 샘플 데이터로 답한다(목 클라이언트는 토큰을 검증하지 않고 고정 계좌를 돌려준다). 그래서
     * 저장하는 토큰은 더미이고 만료만 넉넉히 둔다. 연동 연출 지연은 프론트에서 처리하고 이 경로는
     * 즉시 응답한다. {@code mock-data} 플래그 아래 컨트롤러로만 노출한다.
     *
     * <p>사용자일련번호에 회원 식별자를 박아 두는 이유는 페르소나 때문이다. 오픈뱅킹 조회 메서드에는
     * 회원 식별자가 없어서, 목 클라이언트는 이 값에서 회원을 되짚어 그 회원이 고른 페르소나로 답한다.
     */
    @Transactional
    public OpenBankingConnectionResponse mockConnect(Long memberId) {
        Instant now = Instant.now(clock);
        Instant farFuture = now.plusSeconds(365L * 24 * 3600);
        OpenBankingConnection connection =
                connectionRepository.findByMemberId(memberId).orElseGet(() -> OpenBankingConnection.create(memberId));
        connection.updateCredentials(
                MockPersonaSelection.MOCK_USER_SEQ_NO_PREFIX + memberId,
                tokenCipher.encrypt(memberId, "mock-access-token"),
                tokenCipher.encrypt(memberId, "mock-refresh-token"),
                "Bearer",
                "login inquiry",
                farFuture,
                farFuture,
                now);
        return OpenBankingConnectionResponse.from(connectionRepository.save(connection));
    }

    @Transactional(readOnly = true)
    public OpenBankingConnectionResponse connection(Long memberId) {
        return connectionRepository
                .findByMemberId(memberId)
                .map(OpenBankingConnectionResponse::from)
                .orElseGet(OpenBankingConnectionResponse::disconnected);
    }

    @Transactional
    public List<OpenBankingAccountResponse> accounts(Long memberId) {
        AccessContext context = accessContext(memberId);
        return client.userInfo(context.accessToken(), context.connection().getUserSeqNo()).accounts().stream()
                .map(OpenBankingAccountResponse::from)
                .toList();
    }

    @Transactional
    public OpenBankingBalanceResponse balance(Long memberId, String fintechUseNumber) {
        AccessContext context = accessContext(memberId);
        requireOwnedAccount(context, fintechUseNumber);
        return OpenBankingBalanceResponse.from(client.balance(context.accessToken(), fintechUseNumber));
    }

    @Transactional
    public OpenBankingTransactionPageResponse transactions(
            Long memberId,
            String fintechUseNumber,
            LocalDate fromDate,
            LocalDate toDate,
            String beforeInquiryTraceInfo) {
        validateTransactionPeriod(fromDate, toDate);
        AccessContext context = accessContext(memberId);
        requireOwnedAccount(context, fintechUseNumber);
        return OpenBankingTransactionPageResponse.from(
                client.transactions(context.accessToken(), fintechUseNumber, fromDate, toDate, beforeInquiryTraceInfo));
    }

    @Transactional
    public OpenBankingLoanListResponse loans(Long memberId, List<String> additionalBankCodes) {
        AccessContext context = accessContext(memberId);
        UserInfo userInfo =
                client.userInfo(context.accessToken(), context.connection().getUserSeqNo());
        List<String> bankCodes = bankCodes(userInfo.accounts(), additionalBankCodes);
        List<Loan> loans = fetchLoans(context, bankCodes);
        int unavailableCount = (int)
                loans.stream().filter(loan -> isBlank(loan.accountNumber())).count();
        return new OpenBankingLoanListResponse(
                bankCodes,
                loans.size(),
                unavailableCount,
                loans.stream().map(OpenBankingLoanResponse::from).toList(),
                Instant.now(clock));
    }

    @Transactional
    public OpenBankingFinancialSummaryResponse financialSummary(Long memberId, List<String> additionalBankCodes) {
        AccessContext context = accessContext(memberId);
        UserInfo userInfo =
                client.userInfo(context.accessToken(), context.connection().getUserSeqNo());
        List<String> bankCodes = bankCodes(userInfo.accounts(), additionalBankCodes);
        return new OpenBankingFinancialSummaryAggregator(client, clock)
                .aggregate(context.accessToken(), userInfo, bankCodes);
    }

    private AccessContext accessContext(Long memberId) {
        OpenBankingConnection connection = connectionRepository
                .findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPEN_BANKING_NOT_CONNECTED));
        Instant now = Instant.now(clock);
        if (connection.getAccessTokenExpiresAt().isAfter(now.plusSeconds(REFRESH_SAFETY_SECONDS))) {
            return new AccessContext(connection, tokenCipher.decrypt(memberId, connection.getAccessTokenCiphertext()));
        }
        if (connection.getRefreshTokenExpiresAt() != null
                && !connection.getRefreshTokenExpiresAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_AUTH_REJECTED);
        }
        String currentRefreshToken = tokenCipher.decrypt(memberId, connection.getRefreshTokenCiphertext());
        Token refreshed = client.refreshToken(currentRefreshToken);
        if (refreshed.accessToken() == null || refreshed.accessToken().isBlank() || refreshed.expiresInSeconds() <= 0) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR);
        }
        String nextRefreshToken = isBlank(refreshed.refreshToken()) ? currentRefreshToken : refreshed.refreshToken();
        String nextScope = isBlank(refreshed.scope()) ? connection.getScope() : refreshed.scope();
        String nextTokenType = isBlank(refreshed.tokenType()) ? connection.getTokenType() : refreshed.tokenType();
        Instant refreshExpiresAt = refreshed.refreshTokenExpiresInSeconds() == null
                ? connection.getRefreshTokenExpiresAt()
                : now.plusSeconds(refreshed.refreshTokenExpiresInSeconds());
        connection.updateCredentials(
                connection.getUserSeqNo(),
                tokenCipher.encrypt(memberId, refreshed.accessToken()),
                tokenCipher.encrypt(memberId, nextRefreshToken),
                nextTokenType,
                nextScope,
                now.plusSeconds(refreshed.expiresInSeconds()),
                refreshExpiresAt,
                now);
        connectionRepository.save(connection);
        return new AccessContext(connection, refreshed.accessToken());
    }

    private void requireOwnedAccount(AccessContext context, String fintechUseNumber) {
        boolean owned = client
                .userInfo(context.accessToken(), context.connection().getUserSeqNo())
                .accounts()
                .stream()
                .map(Account::fintechUseNumber)
                .anyMatch(fintechUseNumber::equals);
        if (!owned) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_ACCOUNT_NOT_FOUND);
        }
    }

    private void validateInitialToken(Token token) {
        if (isBlank(token.accessToken())
                || isBlank(token.refreshToken())
                || isBlank(token.userSeqNo())
                || isBlank(token.tokenType())
                || isBlank(token.scope())
                || token.expiresInSeconds() <= 0) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR);
        }
    }

    private List<String> bankCodes(List<Account> accounts, List<String> additionalBankCodes) {
        Set<String> bankCodes = new LinkedHashSet<>();
        accounts.stream().map(Account::bankCode).filter(code -> !isBlank(code)).forEach(bankCodes::add);
        if (additionalBankCodes != null) {
            for (String bankCode : additionalBankCodes) {
                if (bankCode == null || !bankCode.matches("\\d{3}")) {
                    throw new BusinessException(ErrorCode.INVALID_OPEN_BANKING_BANK_CODE);
                }
                bankCodes.add(bankCode);
            }
        }
        return List.copyOf(bankCodes);
    }

    private List<Loan> fetchLoans(AccessContext context, List<String> bankCodes) {
        Map<String, Loan> uniqueLoans = new LinkedHashMap<>();
        for (String bankCode : bankCodes) {
            String traceInfo = null;
            for (int pageNumber = 0; pageNumber < MAX_PAGES; pageNumber++) {
                LoanPage page =
                        client.loans(context.accessToken(), context.connection().getUserSeqNo(), bankCode, traceInfo);
                for (Loan loan : page.loans()) {
                    uniqueLoans.putIfAbsent(loanKey(loan), loan);
                }
                if (!page.hasNextPage()) {
                    break;
                }
                traceInfo = nextTraceInfo(traceInfo, page.nextTraceInfo(), pageNumber);
            }
        }
        return List.copyOf(uniqueLoans.values());
    }

    private String nextTraceInfo(String currentTraceInfo, String nextTraceInfo, int pageNumber) {
        if (isBlank(nextTraceInfo) || nextTraceInfo.equals(currentTraceInfo) || pageNumber == MAX_PAGES - 1) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_PAGINATION_ERROR);
        }
        return nextTraceInfo;
    }

    private String loanKey(Loan loan) {
        String accountIdentifier = isBlank(loan.accountNumber()) ? loan.accountNumberMasked() : loan.accountNumber();
        return loan.bankCode()
                + '|'
                + accountIdentifier
                + '|'
                + loan.accountSequence()
                + '|'
                + loan.productName()
                + '|'
                + loan.accountType();
    }

    private void validateTransactionPeriod(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate) || toDate.isAfter(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.INVALID_TRANSACTION_PERIOD);
        }
    }

    private Instant expiresAt(Instant now, Long expiresInSeconds) {
        return expiresInSeconds == null ? null : now.plusSeconds(expiresInSeconds);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record AccessContext(OpenBankingConnection connection, String accessToken) {}
}
