package com.homerun.global.security.jwt;

import com.homerun.domain.member.entity.Member;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenProvider {

    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
    }

    public String createAccessToken(Member member) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(member.getId()))
                .claim("provider", member.getProvider().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.accessTokenExpirationSeconds())))
                .signWith(signingKey())
                .compact();
    }

    public long accessTokenExpirationSeconds() {
        return properties.accessTokenExpirationSeconds();
    }

    public Long getMemberId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Long.valueOf(subject);
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(ErrorCode.EXPIRED_ACCESS_TOKEN, exception);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_ACCESS_TOKEN, exception);
        }
    }

    private SecretKey signingKey() {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET은 32바이트 이상의 무작위 문자열이어야 합니다.");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
