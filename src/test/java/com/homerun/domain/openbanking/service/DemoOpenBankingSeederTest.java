package com.homerun.domain.openbanking.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class DemoOpenBankingSeederTest {

    private final OpenBankingService openBanking = mock(OpenBankingService.class);
    private final DemoOpenBankingSeeder seeder = new DemoOpenBankingSeeder(openBanking);

    @Test
    void should_establishMockConnection_whenSeed() {
        seeder.seed(7L);

        verify(openBanking).mockConnect(7L);
    }

    @Test
    void should_swallowFailure_soSignupIsNotBlocked() {
        when(openBanking.mockConnect(7L)).thenThrow(new RuntimeException("boom"));

        // 시드 실패가 가입 트랜잭션까지 터뜨리면 안 된다 — 예외를 삼켜야 한다.
        assertThatCode(() -> seeder.seed(7L)).doesNotThrowAnyException();
    }
}
