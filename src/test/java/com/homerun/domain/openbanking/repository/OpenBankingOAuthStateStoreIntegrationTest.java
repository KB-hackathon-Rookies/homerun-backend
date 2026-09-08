package com.homerun.domain.openbanking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 진짜 Redis 로 state 저장·소비와 1회성을 확인한다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OpenBankingOAuthStateStoreIntegrationTest {

    private final OpenBankingOAuthStateStore store;

    OpenBankingOAuthStateStoreIntegrationTest(@Autowired OpenBankingOAuthStateStore store) {
        this.store = store;
    }

    @Test
    @DisplayName("저장한 state 는 한 번 소비하면 memberId 를 주고, 두 번째 소비는 비어 있다")
    void should_consumeOnce() {
        String state = "state-" + System.nanoTime();
        store.save(state, 42L);

        Optional<Long> first = store.consume(state);
        Optional<Long> second = store.consume(state);

        assertThat(first).contains(42L);
        assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("저장한 적 없는 state 는 빈 값을 준다")
    void should_returnEmpty_when_unknownState() {
        assertThat(store.consume("unknown-" + System.nanoTime())).isEmpty();
    }
}
