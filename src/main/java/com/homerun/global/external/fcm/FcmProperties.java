package com.homerun.global.external.fcm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * FCM 설정.
 *
 * @param enabled true 일 때만 실제 Firebase 로 전송한다. 기본 false → 데모/로컬은 무동작.
 * @param credentials 서비스계정 JSON. 원본 JSON, base64 인코딩 JSON, 또는 파일 경로 중 하나.
 */
@ConfigurationProperties("fcm")
public record FcmProperties(@DefaultValue("false") boolean enabled, String credentials) {}
