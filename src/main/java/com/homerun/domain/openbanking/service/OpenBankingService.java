package com.homerun.domain.openbanking.service;

import com.homerun.domain.openbanking.config.OpenBankingTokenCipher;
import com.homerun.domain.openbanking.dto.response.OpenBankingAccountResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingBalanceResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingConnectionResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingTransactionPageResponse;
import com.homerun.domain.openbanking.entity.OpenBankingConnection;
import com.homerun.domain.openbanking.repository.OpenBankingConnectionRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.openbanking.OpenBankingClient;
import com.homerun.global.external.openbanking.OpenBankingResponses.Account;
import com.homerun.global.external.openbanking.OpenBankingResponses.Token;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenBankingService {

    private static final long REFRESH_SAFETY_SECONDS = 30;

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
