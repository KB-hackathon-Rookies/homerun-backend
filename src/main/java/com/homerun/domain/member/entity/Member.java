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
import java.time.LocalDate;

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
    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 20)
    private String phone;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Column(name = "residence_region_id")
    private Long residenceRegionId;

    @Column(name = "detail_address")
    private String detailAddress;

    @Column(name = "military_months", nullable = false)
    private int militaryMonths;

    @Column(name = "military_months_confirmed", nullable = false)
    private boolean militaryMonthsConfirmed;

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

    private Member(AuthProvider provider, String providerId, String email, String name) {
        this.provider = provider;
        this.providerUserId = providerId;
        this.email = email;
        this.name = name;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Member create(AuthProvider provider, String providerId, String email, String name) {
        return new Member(provider, providerId, email, name);
    }

    public static Member createLocal(String email, String passwordHash, String name) {
        Member member = new Member(AuthProvider.LOCAL, email, email, name);
        member.passwordHash = passwordHash;
        member.emailVerifiedAt = Instant.now();
        return member;
    }

    /**
     * 프론트 회원가입(AU-04)이 받는 본인 정보까지 채워 로컬 회원을 만든다. 이메일·휴대전화 인증을
     * 모두 통과한 뒤에만 호출되므로 두 인증 시각을 함께 기록한다.
     */
    public static Member createLocal(
            String email,
            String passwordHash,
            String name,
            LocalDate birthDate,
            String phone,
            Long residenceRegionId,
            String detailAddress) {
        Member member = new Member(AuthProvider.LOCAL, email, email, name);
        member.passwordHash = passwordHash;
        member.emailVerifiedAt = Instant.now();
        member.birthDate = birthDate;
        member.phone = phone;
        member.phoneVerifiedAt = Instant.now();
        member.residenceRegionId = residenceRegionId;
        member.detailAddress = detailAddress;
        return member;
    }

    public void updateName(String name) {
        requireActive();
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public void updateDiagnosisProfile(LocalDate birthDate, Integer militaryMonths) {
        requireActive();
        this.birthDate = birthDate;
        this.militaryMonths = militaryMonths == null ? 0 : militaryMonths;
        this.militaryMonthsConfirmed = militaryMonths != null;
        this.updatedAt = Instant.now();
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Integer getConfirmedMilitaryMonths() {
        return militaryMonthsConfirmed ? militaryMonths : null;
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

    public String getName() {
        return name;
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

    public String getPhone() {
        return phone;
    }

    public Instant getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    public Long getResidenceRegionId() {
        return residenceRegionId;
    }

    public String getDetailAddress() {
        return detailAddress;
    }
}
