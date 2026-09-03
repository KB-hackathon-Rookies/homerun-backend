package com.homerun.global.external.resilience;

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

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }
}
