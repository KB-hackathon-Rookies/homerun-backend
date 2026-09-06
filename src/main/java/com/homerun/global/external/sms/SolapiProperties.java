package com.homerun.global.external.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Solapi(CoolSMS) 문자 발송 자격 증명. 값은 환경 변수로만 주입한다. */
@ConfigurationProperties("app.sms.solapi")
public record SolapiProperties(String apiKey, String apiSecret, String sender) {}
