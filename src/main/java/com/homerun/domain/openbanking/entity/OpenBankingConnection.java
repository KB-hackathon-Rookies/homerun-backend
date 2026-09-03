package com.homerun.domain.openbanking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "open_banking_connection")
public class OpenBankingConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(name = "user_seq_no", nullable = false, length = 20)
    private String userSeqNo;

    @Column(name = "access_token_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String accessTokenCiphertext;

    @Column(name = "refresh_token_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String refreshTokenCiphertext;

    @Column(name = "token_type", nullable = false, length = 20)
    private String tokenType;

    @Column(nullable = false, length = 200)
    private String scope;

    @Column(name = "access_token_expires_at", nullable = false)
    private Instant accessTokenExpiresAt;

    @Column(name = "refresh_token_expires_at")
    private Instant refreshTokenExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OpenBankingConnection() {}

    private OpenBankingConnection(Long memberId) {
        this.memberId = memberId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static OpenBankingConnection create(Long memberId) {
        return new OpenBankingConnection(memberId);
    }

    public void updateCredentials(
            String userSeqNo,
            String accessTokenCiphertext,
            String refreshTokenCiphertext,
            String tokenType,
            String scope,
            Instant accessTokenExpiresAt,
            Instant refreshTokenExpiresAt,
            Instant updatedAt) {
        this.userSeqNo = userSeqNo;
        this.accessTokenCiphertext = accessTokenCiphertext;
        this.refreshTokenCiphertext = refreshTokenCiphertext;
        this.tokenType = tokenType;
        this.scope = scope;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
        this.updatedAt = updatedAt;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getUserSeqNo() {
        return userSeqNo;
    }

    public String getAccessTokenCiphertext() {
        return accessTokenCiphertext;
    }

    public String getRefreshTokenCiphertext() {
        return refreshTokenCiphertext;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getScope() {
        return scope;
    }

    public Instant getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public Instant getRefreshTokenExpiresAt() {
        return refreshTokenExpiresAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
