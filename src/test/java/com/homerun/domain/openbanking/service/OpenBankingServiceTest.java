package com.homerun.domain.openbanking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.openbanking.config.OpenBankingTokenCipher;
import com.homerun.domain.openbanking.dto.response.OpenBankingConnectionResponse;
import com.homerun.domain.openbanking.entity.OpenBankingConnection;
import com.homerun.domain.openbanking.repository.OpenBankingConnectionRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.openbanking.OpenBankingClient;
import com.homerun.global.external.openbanking.OpenBankingResponses;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenBankingServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private OpenBankingConnectionRepository repository;

    @Mock
    private OpenBankingClient client;

    @Mock
    private OpenBankingTokenCipher cipher;

    private OpenBankingService service;

    @BeforeEach
    void setUp() {
        service = new OpenBankingService(repository, client, cipher, CLOCK);
    }

    @Test
    void should_encryptAndSaveTokens_whenConnecting() {
        OpenBankingResponses.Token token = new OpenBankingResponses.Token(
                "access", "refresh", "Bearer", "login inquiry", "1100000000", 3600, 7200L);
        when(client.exchangeAuthorizationCode("code")).thenReturn(token);
        when(cipher.encrypt(MEMBER_ID, "access")).thenReturn("encrypted-access");
        when(cipher.encrypt(MEMBER_ID, "refresh")).thenReturn("encrypted-refresh");
        when(repository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(OpenBankingConnection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OpenBankingConnectionResponse response = service.connect(MEMBER_ID, "code");

        assertThat(response.connected()).isTrue();
        assertThat(response.scope()).isEqualTo("login inquiry");
        verify(repository).save(any(OpenBankingConnection.class));
    }

    @Test
    void should_refreshExpiredAccessToken_beforeFetchingAccounts() {
        OpenBankingConnection connection = connection(NOW.minusSeconds(1), NOW.plusSeconds(7200));
        when(repository.findByMemberIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(connection));
        when(cipher.decrypt(MEMBER_ID, "encrypted-refresh")).thenReturn("refresh");
        when(client.refreshToken("refresh"))
                .thenReturn(
                        new OpenBankingResponses.Token("new-access", "", "Bearer", "login inquiry", "", 3600, null));
        when(cipher.encrypt(MEMBER_ID, "new-access")).thenReturn("new-encrypted-access");
        when(cipher.encrypt(MEMBER_ID, "refresh")).thenReturn("encrypted-refresh");
        when(client.userInfo("new-access", "1100000000"))
                .thenReturn(new OpenBankingResponses.UserInfo("1100000000", "홍길동", List.of()));

        service.accounts(MEMBER_ID);

        verify(client).refreshToken("refresh");
        verify(repository).save(connection);
    }

    @Test
    void should_rejectFintechUseNumber_notOwnedByConnectedUser() {
        OpenBankingConnection connection = connection(NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        when(repository.findByMemberIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(connection));
        when(cipher.decrypt(MEMBER_ID, "encrypted-access")).thenReturn("access");
        when(client.userInfo("access", "1100000000"))
                .thenReturn(new OpenBankingResponses.UserInfo(
                        "1100000000",
                        "홍길동",
                        List.of(new OpenBankingResponses.Account(
                                "급여통장", "004", "국민은행", "", "111111111111111111111111", "123-***", "홍길동", "1"))));

        assertThatThrownBy(() -> service.balance(MEMBER_ID, "222222222222222222222222"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPEN_BANKING_ACCOUNT_NOT_FOUND));
    }

    @Test
    void should_rejectFutureTransactionPeriod() {
        assertThatThrownBy(() -> service.transactions(
                        MEMBER_ID,
                        "123456789012345678901234",
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 4),
                        null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_TRANSACTION_PERIOD));
    }

    private OpenBankingConnection connection(Instant accessExpiresAt, Instant refreshExpiresAt) {
        OpenBankingConnection connection = OpenBankingConnection.create(MEMBER_ID);
        connection.updateCredentials(
                "1100000000",
                "encrypted-access",
                "encrypted-refresh",
                "Bearer",
                "login inquiry",
                accessExpiresAt,
                refreshExpiresAt,
                NOW.minusSeconds(60));
        return connection;
    }
}
