package com.homerun.domain.auth.service;

import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailAuthService {

    private final EmailVerificationService verificationService;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public EmailAuthService(
            EmailVerificationService verificationService,
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder) {
        this.verificationService = verificationService;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Member signup(String rawEmail, String password, String nickname, String verificationToken) {
        String email = verificationService.normalize(rawEmail);
        if (memberRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        verificationService.consumeVerifiedToken(email, verificationToken);
        try {
            return memberRepository.saveAndFlush(
                    Member.createLocal(email, passwordEncoder.encode(password), nickname.trim()));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED, exception);
        }
    }

    @Transactional(readOnly = true)
    public Member login(String rawEmail, String password) {
        String email = verificationService.normalize(rawEmail);
        Member member = memberRepository
                .findByProviderAndProviderUserId(AuthProvider.LOCAL, email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_EMAIL_CREDENTIALS));
        if (member.getPasswordHash() == null || !passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_EMAIL_CREDENTIALS);
        }
        return member;
    }
}
