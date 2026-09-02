package com.homerun.domain.member.entity;

import com.homerun.domain.auth.type.AuthProvider;
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

    @Column(length = 100)
    private String nickname;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
}
