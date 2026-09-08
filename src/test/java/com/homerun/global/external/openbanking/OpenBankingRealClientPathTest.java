package com.homerun.global.external.openbanking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.homerun.domain.openbanking.config.OpenBankingTokenCipher;
import com.homerun.domain.openbanking.dto.response.OpenBankingAccountResponse;
import com.homerun.domain.openbanking.entity.OpenBankingConnection;
import com.homerun.domain.openbanking.repository.OpenBankingConnectionRepository;
import com.homerun.domain.openbanking.service.OpenBankingService;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** 플래그가 꺼진 기본 상태에서는 조회가 실제 업스트림으로 나가고, 연결 없는 회원은 호출 전에 막힌다. */
class OpenBankingRealClientPathTest {

    private static final Long MEMBER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OpenBankingConnectionRepository repository;
    private OpenBankingTokenCipher cipher;
    private MockRestServiceServer server;
    private OpenBankingService service;

    @BeforeEach
    void setUp() {
        repository = mock(OpenBankingConnectionRepository.class);
        cipher = mock(OpenBankingTokenCipher.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        HttpOpenBankingClient client = new HttpOpenBankingClient(
                new OpenBankingProperties(
                        "https://oauth.example.com",
                        "https://api.example.com",
                        "client-id",
                        "client-secret",
                        "1234567890",
                        "https://app.example.com/callback",
                        "login inquiry",
                        "unused",
                        false),
                new ObjectMapper(),
                CLOCK,
                builder.build());
        service = new OpenBankingService(repository, client, cipher, CLOCK);
    }

    @Test
    void should_callTheRealUpstream_whenMockDataIsOff() {
        connected();
        server.expect(requestTo(Matchers.startsWith("https://api.example.com/v2.0/user/me")))
                .andRespond(withSuccess("""
                        {
                          "rsp_code":"A0000",
                          "user_seq_no":"1100000000",
                          "user_name":"실제사용자",
                          "res_list":[
                            {
                              "account_alias":"실제 급여통장",
                              "bank_code_std":"004",
                              "bank_name":"국민은행",
                              "fintech_use_num":"111111111111111111111111",
                              "account_num_masked":"004-**-****-0000",
                              "account_holder_name":"실제사용자",
                              "account_type":"1"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<OpenBankingAccountResponse> accounts = service.accounts(MEMBER_ID);

        server.verify();
        assertThat(accounts).singleElement().satisfies(account -> {
            assertThat(account.alias()).isEqualTo("실제 급여통장");
            assertThat(account.fintechUseNumber()).isEqualTo("111111111111111111111111");
        });
    }

    @Test
    void should_failTheSameWay_forAMemberWithoutConnection_whenMockDataIsOff() {
        when(repository.findByMemberIdForUpdate(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.financialSummary(MEMBER_ID, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPEN_BANKING_NOT_CONNECTED));
        assertThatThrownBy(() -> service.accounts(MEMBER_ID)).isInstanceOf(BusinessException.class);
        server.verify();
    }

    private void connected() {
        OpenBankingConnection connection = OpenBankingConnection.create(MEMBER_ID);
        connection.updateCredentials(
                "1100000000",
                "encrypted-access",
                "encrypted-refresh",
                "Bearer",
                "login inquiry",
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200),
                NOW.minusSeconds(60));
        when(repository.findByMemberIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(connection));
        when(cipher.decrypt(MEMBER_ID, "encrypted-access")).thenReturn("access");
    }
}
