package com.homerun.global.external.aicoach;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI 코치(FastAPI) 연결 설정.
 *
 * <p>ai-coach 는 외부에 공개하지 않고 Spring 만 호출하는 내부 서비스다. 기본값은 로컬 개발용이며
 * 배포 환경에서는 {@code AI_COACH_BASE_URL} 로 컨테이너 주소를 넣는다.
 *
 * <p>응답 제한시간은 공통값({@code external-api.resilience.read-timeout}) 을 쓰지 않는다. ai-coach 가
 * 내부에서 LLM 을 호출하므로 정상 응답도 공통 5초를 넘기는 것이 보통이다.
 */
@ConfigurationProperties("external-api.ai-coach")
public record AiCoachProperties(
        @DefaultValue("http://localhost:8000") String baseUrl,
        @DefaultValue("20s") Duration readTimeout) {

    public AiCoachProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("AI 코치 base URL 은 비어 있을 수 없습니다.");
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("AI 코치 응답 제한시간은 0보다 커야 합니다.");
        }
    }
}
