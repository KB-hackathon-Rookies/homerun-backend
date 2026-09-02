package com.homerun.domain.auth.service;

import com.homerun.domain.auth.config.EmailVerificationProperties;
import com.homerun.domain.auth.dto.response.EmailVerificationResponse;
import com.homerun.domain.auth.repository.EmailVerificationStore;
import com.homerun.domain.auth.repository.EmailVerificationStore.SendPermit;
import com.homerun.domain.auth.repository.EmailVerificationStore.VerificationResult;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.mail.VerificationEmailSender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final EmailVerificationProperties properties;
    private final EmailVerificationStore verificationStore;
    private final VerificationEmailSender emailSender;
    private final MemberRepository memberRepository;

    public EmailVerificationService(
            EmailVerificationProperties properties,
            EmailVerificationStore verificationStore,
            VerificationEmailSender emailSender,
            MemberRepository memberRepository) {
        this.properties = properties;
        this.verificationStore = verificationStore;
        this.emailSender = emailSender;
        this.memberRepository = memberRepository;
    }

    public void sendCode(String rawEmail) {
        validateConfiguration();
        String email = normalize(rawEmail);
        rejectRegisteredEmail(email);
        String emailHash = sha256(email);
        SendPermit permit = verificationStore.acquireSendPermit(
                emailHash, properties.maxSendsPerWindow(), properties.sendWindow(), properties.resendCooldown());
        if (permit != SendPermit.GRANTED) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_RATE_LIMITED);
        }

        String code = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
        verificationStore.saveCode(emailHash, codeDigest(email, code), properties.codeTtl());
        try {
            emailSender.sendVerificationCode(email, code, properties.codeTtl().toMinutes());
        } catch (BusinessException exception) {
            verificationStore.removePendingCode(emailHash);
            throw exception;
        }
    }

    public EmailVerificationResponse confirmCode(String rawEmail, String code) {
        validateConfiguration();
        String email = normalize(rawEmail);
        VerificationResult result =
                verificationStore.verifyCode(sha256(email), codeDigest(email, code), properties.maxAttempts());
        switch (result) {
            case EXPIRED -> throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
            case INVALID -> throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_INVALID);
            case ATTEMPTS_EXCEEDED -> throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED);
            case VERIFIED -> {
                String token = createToken();
                verificationStore.saveVerifiedToken(sha256(token), sha256(email), properties.verifiedTokenTtl());
                return new EmailVerificationResponse(
                        token, properties.verifiedTokenTtl().toSeconds());
            }
        }
        throw new IllegalStateException("처리할 수 없는 이메일 인증 결과입니다.");
    }

    public void consumeVerifiedToken(String rawEmail, String token) {
        String email = normalize(rawEmail);
        if (!verificationStore.consumeVerifiedToken(sha256(token), sha256(email))) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID);
        }
    }

    public String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void rejectRegisteredEmail(String email) {
        if (memberRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
    }

    private void validateConfiguration() {
        if (properties.secret() == null || properties.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new BusinessException(ErrorCode.EMAIL_AUTH_CONFIGURATION_ERROR);
        }
    }

    private String createToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String codeDigest(String email, String code) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal((email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("이메일 인증번호 해시에 실패했습니다.", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("이메일 인증 키 해시에 실패했습니다.", exception);
        }
    }
}
