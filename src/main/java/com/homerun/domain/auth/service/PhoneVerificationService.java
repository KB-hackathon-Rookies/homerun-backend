package com.homerun.domain.auth.service;

import com.homerun.domain.auth.config.PhoneVerificationProperties;
import com.homerun.domain.auth.dto.response.PhoneVerificationResponse;
import com.homerun.domain.auth.dto.response.PhoneVerificationSendResponse;
import com.homerun.domain.auth.repository.PhoneVerificationStore;
import com.homerun.domain.auth.repository.PhoneVerificationStore.SendPermit;
import com.homerun.domain.auth.repository.PhoneVerificationStore.VerificationResult;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.sms.SmsSender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * 휴대전화 인증. {@link EmailVerificationService} 와 같은 구조(HMAC 코드 다이제스트, SHA-256 키,
 * Redis 레이트리밋, 인증 완료 토큰 1회 소비)이며, 발송만 {@link SmsSender} 로 한다.
 *
 * <p>번호는 저장·비교 전에 항상 숫자만 남기도록 정규화한다(010-1234-5678 → 01012345678).
 */
@Service
public class PhoneVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final PhoneVerificationProperties properties;
    private final PhoneVerificationStore verificationStore;
    private final SmsSender smsSender;
    private final MemberRepository memberRepository;

    public PhoneVerificationService(
            PhoneVerificationProperties properties,
            PhoneVerificationStore verificationStore,
            SmsSender smsSender,
            MemberRepository memberRepository) {
        this.properties = properties;
        this.verificationStore = verificationStore;
        this.smsSender = smsSender;
        this.memberRepository = memberRepository;
    }

    public PhoneVerificationSendResponse sendCode(String rawPhone) {
        validateConfiguration();
        String phone = normalize(rawPhone);
        rejectRegisteredPhone(phone);
        String phoneHash = sha256(phone);
        SendPermit permit = verificationStore.acquireSendPermit(
                phoneHash, properties.maxSendsPerWindow(), properties.sendWindow(), properties.resendCooldown());
        if (permit != SendPermit.GRANTED) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_RATE_LIMITED);
        }

        String code = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
        verificationStore.saveCode(phoneHash, codeDigest(phone, code), properties.codeTtl());
        try {
            smsSender.sendVerificationCode(phone, code, properties.codeTtl().toMinutes());
        } catch (BusinessException exception) {
            verificationStore.removePendingCode(phoneHash);
            throw exception;
        }
        return new PhoneVerificationSendResponse(
                properties.codeTtl().toSeconds(), properties.resendCooldown().toSeconds());
    }

    public PhoneVerificationResponse confirmCode(String rawPhone, String code) {
        validateConfiguration();
        String phone = normalize(rawPhone);
        VerificationResult result =
                verificationStore.verifyCode(sha256(phone), codeDigest(phone, code), properties.maxAttempts());
        switch (result) {
            case EXPIRED -> throw new BusinessException(ErrorCode.PHONE_VERIFICATION_EXPIRED);
            case INVALID -> throw new BusinessException(ErrorCode.PHONE_VERIFICATION_INVALID);
            case ATTEMPTS_EXCEEDED -> throw new BusinessException(ErrorCode.PHONE_VERIFICATION_ATTEMPTS_EXCEEDED);
            case VERIFIED -> {
                String token = createToken();
                verificationStore.saveVerifiedToken(sha256(token), sha256(phone), properties.verifiedTokenTtl());
                return new PhoneVerificationResponse(
                        token, properties.verifiedTokenTtl().toSeconds());
            }
        }
        throw new IllegalStateException("처리할 수 없는 휴대전화 인증 결과입니다.");
    }

    /** 가입 트랜잭션에서 호출한다. 인증 완료 토큰을 1회 소비해 같은 토큰 재사용을 막는다. */
    public void consumeVerifiedToken(String rawPhone, String token) {
        String phone = normalize(rawPhone);
        if (!verificationStore.consumeVerifiedToken(sha256(token), sha256(phone))) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_TOKEN_INVALID);
        }
    }

    /** 숫자만 남긴다. 국내 휴대전화(01X-XXXX-XXXX) 형태가 아니면 형식 오류로 본다. */
    public String normalize(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (!digits.matches("01[0-9]\\d{7,8}")) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_INVALID);
        }
        return digits;
    }

    private void rejectRegisteredPhone(String phone) {
        if (memberRepository.existsByPhoneAndDeletedAtIsNull(phone)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
    }

    private void validateConfiguration() {
        if (properties.secret() == null || properties.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new BusinessException(ErrorCode.PHONE_AUTH_CONFIGURATION_ERROR);
        }
    }

    private String createToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String codeDigest(String phone, String code) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal((phone + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("휴대전화 인증번호 해시에 실패했습니다.", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("휴대전화 인증 키 해시에 실패했습니다.", exception);
        }
    }
}
