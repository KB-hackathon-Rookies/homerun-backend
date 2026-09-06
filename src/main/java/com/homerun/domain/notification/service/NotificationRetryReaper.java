package com.homerun.domain.notification.service;

import com.homerun.domain.notification.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 재처리 리퍼. "ACK 안 함 = 자동 재시도" 가 아니므로, PEL 에 일정 시간 이상 남은 엔트리를 명시적으로
 * 회수(XCLAIM)해 재전송한다.
 *
 * <p>재시도 카운트가 상한을 넘으면 알림을 FAILED 로 확정하고 XACK 로 스트림에서 걷어낸다.
 * 이렇게 해야 실패가 PEL 에 무한 적체되지 않는다.
 */
@Component
public class NotificationRetryReaper {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryReaper.class);

    private final StringRedisTemplate redisTemplate;
    private final NotificationDeliveryService deliveryService;
    private final NotificationProperties properties;

    public NotificationRetryReaper(
            StringRedisTemplate redisTemplate,
            NotificationDeliveryService deliveryService,
            NotificationProperties properties) {
        this.redisTemplate = redisTemplate;
        this.deliveryService = deliveryService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${notification.reaper.fixed-delay:60s}")
    public void reap() {
        String key = properties.stream().key();
        String group = properties.stream().group();
        String consumer = properties.stream().consumer();

        PendingMessages pending = redisTemplate
                .opsForStream()
                .pending(key, group, Range.unbounded(), properties.reaper().batchSize());
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (PendingMessage message : pending) {
            if (message.getElapsedTimeSinceLastDelivery()
                            .compareTo(properties.reaper().minIdle())
                    < 0) {
                continue; // 아직 살아있는 처리일 수 있다. 충분히 idle 한 것만 회수한다.
            }
            reclaimAndRetry(key, group, consumer, message.getId());
        }
    }

    private void reclaimAndRetry(String key, String group, String consumer, RecordId recordId) {
        var claimed = redisTemplate
                .opsForStream()
                .claim(key, group, consumer, properties.reaper().minIdle(), recordId);
        if (claimed == null || claimed.isEmpty()) {
            return; // 다른 워커가 먼저 가져갔다.
        }
        for (MapRecord<String, Object, Object> record : claimed) {
            Object raw = record.getValue().get("notificationId");
            if (raw == null) {
                redisTemplate.opsForStream().acknowledge(key, group, record.getId());
                continue;
            }
            Long notificationId = Long.valueOf(raw.toString());
            if (deliveryService.exhaustedOrPrepareRetry(
                    notificationId, properties.reaper().maxRetries())) {
                redisTemplate.opsForStream().acknowledge(key, group, record.getId());
                continue;
            }
            try {
                deliveryService.deliver(notificationId);
                redisTemplate.opsForStream().acknowledge(key, group, record.getId());
            } catch (RuntimeException exception) {
                // 여전히 실패. ACK 하지 않으면 다음 주기에 다시 회수된다(카운트는 이미 1 증가).
                log.warn("리퍼 재전송 실패, 다음 주기 재시도: id={}", notificationId, exception);
            }
        }
    }
}
