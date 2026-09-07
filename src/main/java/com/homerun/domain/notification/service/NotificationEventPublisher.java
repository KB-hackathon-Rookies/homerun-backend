package com.homerun.domain.notification.service;

import com.homerun.domain.notification.config.NotificationProperties;
import com.homerun.domain.notification.entity.Notification;
import com.homerun.domain.notification.repository.NotificationRepository;
import com.homerun.domain.notification.type.NotificationType;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/**
 * 알림 프로듀서. 도메인 이벤트/스케줄러가 호출해 알림을 예약한다.
 *
 * <p>동작: {@code notification} 행을 PENDING 으로 저장하고, 트랜잭션 커밋 이후에 스트림으로
 * id 만 XADD 한다. 커밋 전에 발행하면 컨슈머가 아직 커밋되지 않은 행을 읽어 "없는 알림"으로
 * 오인할 수 있어, afterCommit 시점에 발행한다.
 *
 * <p>{@code dedupKey} 가 있으면 유니크 제약으로 중복 예약을 막는다. 이미 있으면 조용히 skip.
 */
@Service
public class NotificationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventPublisher.class);

    private final NotificationRepository notificationRepository;
    private final StringRedisTemplate redisTemplate;
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationEventPublisher(
            NotificationRepository notificationRepository,
            StringRedisTemplate redisTemplate,
            NotificationProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.notificationRepository = notificationRepository;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 알림을 예약한다.
     *
     * @param dedupKey 멱등키. null 이면 즉석 알림(중복 검사 없음).
     * @return 새로 만든 알림 id. 중복으로 skip 되면 빈 값.
     */
    @Transactional
    public Optional<Long> publish(
            Long memberId,
            NotificationType type,
            String title,
            String body,
            Map<String, String> data,
            String dedupKey) {
        if (dedupKey != null && notificationRepository.existsByDedupKey(dedupKey)) {
            return Optional.empty();
        }
        Notification saved;
        try {
            saved = notificationRepository.saveAndFlush(
                    Notification.pending(memberId, type, title, body, toJson(data), dedupKey, clock.instant()));
        } catch (DataIntegrityViolationException duplicate) {
            // 동시 실행으로 dedup_key 유니크 위반 → 이미 다른 실행이 예약함.
            return Optional.empty();
        }
        enqueueAfterCommit(saved.getId());
        return Optional.of(saved.getId());
    }

    private void enqueueAfterCommit(Long notificationId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueueExisting(notificationId);
                }
            });
        } else {
            enqueueExisting(notificationId);
        }
    }

    public boolean enqueueExisting(Long notificationId) {
        try {
            redisTemplate
                    .opsForStream()
                    .add(properties.stream().key(), Map.of("notificationId", String.valueOf(notificationId)));
            return true;
        } catch (RuntimeException exception) {
            // PENDING 행은 복구 스케줄러가 다시 발행한다.
            log.error("알림 스트림 발행 실패: notificationId={}", notificationId, exception);
            return false;
        }
    }

    private String toJson(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        // Jackson 3 은 직렬화 실패를 unchecked(JacksonException)로 던진다.
        return objectMapper.writeValueAsString(data);
    }
}
