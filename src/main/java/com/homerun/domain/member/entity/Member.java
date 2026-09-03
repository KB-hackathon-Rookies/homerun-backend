package com.homerun.domain.member.entity;

import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId;

    @Column(length = 320)
    private String email;

    @Column(length = 50)
    private String nickname;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Member() {}

    private Member(AuthProvider provider, String providerId, String email, String nickname) {
        this.provider = provider;
        this.providerUserId = providerId;
        this.email = email;
        this.nickname = nickname;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Member create(AuthProvider provider, String providerId, String email, String nickname) {
        return new Member(provider, providerId, email, nickname);
    }

    public static Member createLocal(String email, String passwordHash, String nickname) {
        Member member = new Member(AuthProvider.LOCAL, email, email, nickname);
        member.passwordHash = passwordHash;
        member.emailVerifiedAt = Instant.now();
        return member;
    }

    public void updateNickname(String nickname) {
        requireActive();
        this.nickname = nickname;
        this.updatedAt = Instant.now();
    }

    public void withdraw() {
        requireActive();
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }

    public void requireActive() {
        if (deletedAt != null) {
            throw new BusinessException(ErrorCode.MEMBER_WITHDRAWN);
        }
    }

    public Long getId() {
        return id;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
