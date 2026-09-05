package com.homerun.domain.notification.entity;

import com.homerun.domain.notification.type.NotificationStatus;
import com.homerun.domain.notification.type.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 알림 한 건. 발송 이력이자 사용자 인박스의 한 줄이다.
 *
 * <p>스트림에는 이 행의 id 만 실려 나가고 본문(title·body·data)의 원본은 여기다. 컨슈머가
 * id 로 다시 읽어 FCM 으로 보낸다.
 *
 * <p>{@code dedupKey} 는 스케줄러가 같은 마감/넛지를 하루에 여러 번 돌려도 1건만 만들도록
 * 막는 멱등키다. 즉석 알림은 null 로 두어 제약을 타지 않는다.
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    /** 프론트 딥링크용 부가 데이터(JSON 문자열). 서버가 질의하지 않으므로 문자열로만 다룬다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "dedup_key", length = 200)
    private String dedupKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {}

    private Notification(
            Long memberId,
            NotificationType type,
            String title,
            String body,
            String data,
            String dedupKey,
            Instant now) {
        this.memberId = memberId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.data = data;
        this.dedupKey = dedupKey;
        this.status = NotificationStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = now;
    }

    public static Notification pending(
            Long memberId,
            NotificationType type,
            String title,
            String body,
            String data,
            String dedupKey,
            Instant now) {
        return new Notification(memberId, type, title, body, data, dedupKey, now);
    }

    public void markSent(Instant now) {
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
    }

    /** 재시도 상한까지 실패해 리퍼가 최종 실패로 확정할 때. */
    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }

    public void increaseRetryCount() {
        this.retryCount += 1;
    }

    public void markRead(Instant now) {
        if (this.readAt == null) {
            this.readAt = now;
            this.status = NotificationStatus.READ;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getData() {
        return data;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
