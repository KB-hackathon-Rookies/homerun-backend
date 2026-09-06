package com.homerun.global.external.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 실제 발송 없이 로그만 남기는 스텁. Solapi 등 실 구현체가 등록되지 않았을 때 {@link SmsConfig} 가
 * 이 빈을 대체용으로 올린다. 제공사 계정·발신번호가 준비되면 실 구현체를 빈으로 추가하기만 하면
 * 이 스텁은 자동으로 물러난다.
 */
public class StubSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(StubSmsSender.class);

    @Override
    public void sendVerificationCode(String recipient, String code, long expiresInMinutes) {
        log.info("[SMS-STUB] 인증번호 발송(실발송 아님) to={}, code={}, expiresInMinutes={}", recipient, code, expiresInMinutes);
    }
}
