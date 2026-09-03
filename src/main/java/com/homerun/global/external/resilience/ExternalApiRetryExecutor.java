package com.homerun.global.external.resilience;

import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ExternalApiRetryExecutor {

    private final ExternalApiResilienceProperties properties;
    private final Sleeper sleeper;

    @Autowired
    public ExternalApiRetryExecutor(ExternalApiResilienceProperties properties) {
        this(properties, Thread::sleep);
    }

    public static ExternalApiRetryExecutor noRetry() {
        return new ExternalApiRetryExecutor(
                new ExternalApiResilienceProperties(Duration.ofSeconds(1), Duration.ofSeconds(1), 1, Duration.ZERO));
    }

    ExternalApiRetryExecutor(ExternalApiResilienceProperties properties, Sleeper sleeper) {
        this.properties = properties;
        this.sleeper = sleeper;
    }

    public <T> T execute(Supplier<T> request) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                return request.get();
            } catch (RuntimeException exception) {
                lastException = exception;
                if (attempt == properties.maxAttempts() || !isRetryable(exception)) {
                    throw exception;
                }
                waitBeforeRetry(attempt);
            }
        }
        throw lastException;
    }

    private boolean isRetryable(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof ResourceAccessException) {
                return true;
            }
            if (cause instanceof RestClientResponseException responseException) {
                HttpStatusCode status = responseException.getStatusCode();
                return status.value() == 429 || status.is5xxServerError();
            }
        }
        return false;
    }

    private void waitBeforeRetry(int completedAttempts) {
        try {
            sleeper.sleep(properties.retryBackoff().toMillis() * completedAttempts);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalApiRetryInterruptedException(exception);
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private static final class ExternalApiRetryInterruptedException extends RuntimeException {
        private ExternalApiRetryInterruptedException(InterruptedException cause) {
            super("외부 API 재시도 대기 중 요청이 중단되었습니다.", cause);
        }
    }
}
