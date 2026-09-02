package com.homerun.global.external.mail;

public interface VerificationEmailSender {
    void sendVerificationCode(String recipient, String code, long expiresInMinutes);
}
