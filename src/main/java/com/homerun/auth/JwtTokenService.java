package com.homerun.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JwtTokenService {

    private final JwtProperties properties;

    public JwtTokenService(JwtProperties properties) {
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

    public Long getMemberId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access Token이 필요합니다.");
        }
        try {
            String subject = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(authorizationHeader.substring("Bearer ".length()))
                    .getPayload()
                    .getSubject();
            return Long.valueOf(subject);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 Access Token입니다.", exception);
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
