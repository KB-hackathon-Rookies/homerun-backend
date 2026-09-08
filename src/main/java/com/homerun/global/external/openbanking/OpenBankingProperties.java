package com.homerun.global.external.openbanking;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param mockData 계좌·거래 조회를 샘플 픽스처로 대체할지 여부. 금융결제원 오픈뱅킹의 조회 API 는 사업자
 *     등록을 마친 이용기관만 호출할 수 있어, 등록 전 데모에서만 켠다. 인가(OAuth)는 이 값과 무관하게 항상
 *     실제로 호출된다. 기본값 false 라 운영·CI 는 실제 업스트림을 그대로 쓴다.
 */
@ConfigurationProperties("external-api.open-banking")
public record OpenBankingProperties(
        String oauthBaseUrl,
        String apiBaseUrl,
        String clientId,
        String clientSecret,
        String clientUseCode,
        String redirectUri,
        String scope,
        String tokenEncryptionKey,
        boolean mockData) {}
