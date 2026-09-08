package com.homerun.global.external.resilience;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ExternalApiRestClientFactory {

    private final ExternalApiResilienceProperties properties;

    public ExternalApiRestClientFactory(ExternalApiResilienceProperties properties) {
        this.properties = properties;
    }

    public RestClient create() {
        return RestClient.builder().requestFactory(requestFactory()).build();
    }

    public RestClient create(String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory())
                .build();
    }

    /**
     * 공통 응답 제한시간보다 오래 걸리는 것이 정상인 업스트림 전용. LLM 호출처럼 수 초가 기본인
     * 곳에 공통 5초를 그대로 쓰면 정상 응답을 타임아웃으로 오해한다.
     */
    public RestClient create(String baseUrl, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = requestFactory();
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }
}
