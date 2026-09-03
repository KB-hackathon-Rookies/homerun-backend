package com.homerun.global.external.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class ExternalApiRetryExecutorTest {

    @Test
    void should_retryTransientNetworkFailure_withIncreasingBackoff() {
        List<Long> sleeps = new ArrayList<>();
        ExternalApiRetryExecutor executor = new ExternalApiRetryExecutor(properties(3), sleeps::add);
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ResourceAccessException("timeout", new SocketTimeoutException());
            }
            return "success";
        });

        assertThat(result).isEqualTo("success");
        assertThat(attempts).hasValue(3);
        assertThat(sleeps).containsExactly(10L, 20L);
    }

    @Test
    void should_notRetryClientError() {
        AtomicInteger attempts = new AtomicInteger();
        ExternalApiRetryExecutor executor = new ExternalApiRetryExecutor(properties(3), millis -> {});

        assertThatThrownBy(() -> executor.execute(() -> {
                    attempts.incrementAndGet();
                    throw new HttpClientErrorException(HttpStatus.BAD_REQUEST);
                }))
                .isInstanceOf(HttpClientErrorException.class);

        assertThat(attempts).hasValue(1);
    }

    @Test
    void should_rejectExcessiveRetryConfiguration() {
        assertThatThrownBy(() -> new ExternalApiResilienceProperties(
                        Duration.ofSeconds(1), Duration.ofSeconds(1), 6, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ExternalApiResilienceProperties properties(int maxAttempts) {
        return new ExternalApiResilienceProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(1), maxAttempts, Duration.ofMillis(10));
    }
}
