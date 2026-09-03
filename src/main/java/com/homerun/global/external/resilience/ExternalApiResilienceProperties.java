package com.homerun.global.external.resilience;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "external-api.resilience")
public record ExternalApiResilienceProperties(
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        @DefaultValue("2") int maxAttempts,
        @DefaultValue("200ms") Duration retryBackoff) {

    public ExternalApiResilienceProperties {
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("외부 API 연결 제한시간은 0보다 커야 합니다.");
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("외부 API 응답 제한시간은 0보다 커야 합니다.");
        }
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("외부 API 최대 시도 횟수는 1~5여야 합니다.");
        }
        if (retryBackoff == null || retryBackoff.isNegative()) {
            throw new IllegalArgumentException("외부 API 재시도 대기시간은 0 이상이어야 합니다.");
        }
    }
}
