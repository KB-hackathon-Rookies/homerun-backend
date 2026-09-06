package com.homerun.domain.notification.entity;

import com.homerun.domain.notification.type.DevicePlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원의 FCM 디바이스 토큰 한 건.
 *
 * <p>같은 토큰이 다시 등록되면 새 행을 만들지 않고 {@link #touch(Instant)} 로 last_seen 만
 * 갱신한다(토큰 유니크). member 는 여러 기기를 가질 수 있다.
 */
@Entity
@Table(name = "device_token")
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected DeviceToken() {}

    public DeviceToken(Long memberId, String token, DevicePlatform platform, Instant now) {
        this.memberId = memberId;
        this.token = token;
        this.platform = platform;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    /** 같은 토큰 재등록 시 소유자·플랫폼·마지막 사용 시각을 갱신한다(기기 주인이 바뀌는 경우 포함). */
    public void touch(Long memberId, DevicePlatform platform, Instant now) {
        this.memberId = memberId;
        this.platform = platform;
        this.lastSeenAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getToken() {
        return token;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
