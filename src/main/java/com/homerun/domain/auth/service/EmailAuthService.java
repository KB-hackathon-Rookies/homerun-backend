package com.homerun.domain.auth.service;

import com.homerun.domain.auth.dto.request.LocalSignupRequest;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.region.repository.RegionRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailAuthService {

    private final EmailVerificationService emailVerificationService;
    private final PhoneVerificationService phoneVerificationService;
    private final MemberRepository memberRepository;
    private final RegionRepository regionRepository;
    private final PasswordEncoder passwordEncoder;

    public EmailAuthService(
            EmailVerificationService emailVerificationService,
            PhoneVerificationService phoneVerificationService,
            MemberRepository memberRepository,
            RegionRepository regionRepository,
            PasswordEncoder passwordEncoder) {
        this.emailVerificationService = emailVerificationService;
        this.phoneVerificationService = phoneVerificationService;
        this.memberRepository = memberRepository;
        this.regionRepository = regionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Member signup(LocalSignupRequest request) {
        // DTO 검증을 우회한 호출도 방어한다 — 필수값이 비면 여기서 막는다.
        requireLocalSignupFields(request);

        String email = emailVerificationService.normalize(request.email());
        String phone = phoneVerificationService.normalize(request.phone());
        if (memberRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        if (memberRepository.existsByPhoneAndDeletedAtIsNull(phone)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
        if (!regionRepository.existsById(request.regionId())) {
            throw new BusinessException(ErrorCode.SIGNUP_REQUIRED_FIELD_MISSING);
        }

        // 이메일·휴대전화 인증 완료 토큰을 각각 1회 소비한다(같은 토큰 반복 가입 차단).
        emailVerificationService.consumeVerifiedToken(email, request.emailVerificationToken());
        phoneVerificationService.consumeVerifiedToken(phone, request.phoneVerificationToken());

        try {
            return memberRepository.saveAndFlush(Member.createLocal(
                    email,
                    passwordEncoder.encode(request.password()),
                    request.name().trim(),
                    request.birthDate(),
                    phone,
                    request.regionId(),
                    blankToNull(request.detailAddress())));
        } catch (DataIntegrityViolationException exception) {
            // 동시 가입 경합으로 이메일·휴대전화 유니크 제약에 걸린 경우.
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED, exception);
        }
    }

    @Transactional(readOnly = true)
    public Member login(String rawEmail, String password) {
        String email = emailVerificationService.normalize(rawEmail);
        Member member = memberRepository
                .findByProviderAndProviderUserIdAndDeletedAtIsNull(AuthProvider.LOCAL, email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_EMAIL_CREDENTIALS));
        member.requireActive();
        if (member.getPasswordHash() == null || !passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_EMAIL_CREDENTIALS);
        }
        return member;
    }

    private void requireLocalSignupFields(LocalSignupRequest request) {
        boolean missing = request == null
                || isBlank(request.email())
                || isBlank(request.password())
                || isBlank(request.name())
                || request.birthDate() == null
                || isBlank(request.phone())
                || request.regionId() == null
                || isBlank(request.emailVerificationToken())
                || isBlank(request.phoneVerificationToken());
        if (missing) {
            throw new BusinessException(ErrorCode.SIGNUP_REQUIRED_FIELD_MISSING);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
