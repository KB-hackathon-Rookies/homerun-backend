package com.homerun.domain.auth;

import com.homerun.domain.member.Member;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenProperties properties;
    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenService(RefreshTokenProperties properties, RefreshTokenRepository refreshTokenRepository) {
        this.properties = properties;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public String issue(Member member) {
        String token = createToken();
        refreshTokenRepository.save(RefreshToken.create(member, hash(token), expiresAt()));
        return token;
    }

    @Transactional
    public Member rotate(String token) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(hash(token))
                .orElseThrow(() -> unauthorized("유효하지 않은 Refresh Token입니다."));
        if (!refreshToken.isUsableAt(Instant.now())) {
            refreshToken.revoke();
            throw unauthorized("만료되었거나 폐기된 Refresh Token입니다.");
        }
        refreshToken.revoke();
        return refreshToken.getMember();
    }

    @Transactional
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(token)).ifPresent(RefreshToken::revoke);
    }

    private String createToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Instant expiresAt() {
        return Instant.now().plusSeconds(properties.expirationDays() * 24 * 60 * 60);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Refresh Token 해시에 실패했습니다.", exception);
        }
    }

    private ResponseStatusException unauthorized(String reason) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
    }
}
