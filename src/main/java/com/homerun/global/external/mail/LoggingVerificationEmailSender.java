package com.homerun.global.external.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * SMTP 계정이 없을 때 인증번호를 로그로만 남기는 목 발송기.
 *
 * <p>메일이 안 나가면 회원가입이 첫 화면에서 막힌다. 로컬에서는 로그에 찍힌 번호를 그대로 입력해
 * 진행한다. 운영에서는 MAIL_USERNAME 이 채워지므로 이 빈이 아예 등록되지 않는다.
 */
@Component
@Primary
@ConditionalOnExpression("'${spring.mail.username:}'.isBlank()")
public class LoggingVerificationEmailSender implements VerificationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingVerificationEmailSender.class);

    @Override
    public void sendVerificationCode(String recipient, String code, long expiresInMinutes) {
        log.warn("[MOCK MAIL] {} 인증번호 {} ({}분 유효) — SMTP 미설정이라 발송하지 않았다", recipient, code, expiresInMinutes);
    }
}
