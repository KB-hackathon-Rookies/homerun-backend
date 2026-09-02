package com.homerun.global.external.mail;

import com.homerun.domain.auth.config.EmailVerificationProperties;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class GmailVerificationEmailSender implements VerificationEmailSender {

    private final JavaMailSender mailSender;
    private final EmailVerificationProperties properties;

    public GmailVerificationEmailSender(JavaMailSender mailSender, EmailVerificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendVerificationCode(String recipient, String code, long expiresInMinutes) {
        if (properties.from() == null || properties.from().isBlank()) {
            throw new BusinessException(ErrorCode.EMAIL_AUTH_CONFIGURATION_ERROR);
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(recipient);
        message.setSubject("[HomeRun] 이메일 인증번호");
        message.setText("HomeRun 회원가입 인증번호는 " + code + "입니다.\n" + expiresInMinutes + "분 안에 입력해 주세요.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED, exception);
        }
    }
}
