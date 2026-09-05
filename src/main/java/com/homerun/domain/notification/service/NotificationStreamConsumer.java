package com.homerun.domain.notification.service;

import com.homerun.domain.notification.config.NotificationProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 알림 스트림 라이브 컨슈머. 컨슈머 그룹에서 새 엔트리(">")를 주기적으로 읽어 전송한다.
 *
 * <p>전송 성공 시 XACK 한다. 실패하면 ACK 하지 않고 엔트리를 PEL 에 남긴다 — 자동 재전달은
 * 일어나지 않으며, {@link NotificationRetryReaper} 가 XAUTOCLAIM 성 회수로 재시도한다.
 * (StreamMessageListenerContainer 대신 스케줄 폴러를 쓰는 이유는 직렬화·제네릭을 단순화하고
 * 재시도 경로를 명시적으로 통제하기 위함이다. 마감 알림은 준실시간이면 충분하다.)
 */
@Component
public class NotificationStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamConsumer.class);

    private final StringRedisTemplate redisTemplate;
    private final NotificationDeliveryService deliveryService;
    private final NotificationProperties properties;

    public NotificationStreamConsumer(
            StringRedisTemplate redisTemplate,
            NotificationDeliveryService deliveryService,
            NotificationProperties properties) {
        this.redisTemplate = redisTemplate;
        this.deliveryService = deliveryService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${notification.stream.poll-interval:1s}")
    public void poll() {
        String key = properties.stream().key();
        String group = properties.stream().group();
        Consumer consumer = Consumer.from(group, properties.stream().consumer());

        List<MapRecord<String, Object, Object>> records = redisTemplate
                .opsForStream()
                .read(
                        consumer,
                        StreamReadOptions.empty().count(properties.stream().batchSize()),
                        StreamOffset.create(key, ReadOffset.lastConsumed()));
        if (records == null || records.isEmpty()) {
            return;
        }
        for (MapRecord<String, Object, Object> record : records) {
            handle(key, group, record);
        }
    }

    private void handle(String key, String group, MapRecord<String, Object, Object> record) {
        Object raw = record.getValue().get("notificationId");
        if (raw == null) {
            // 초기화 레코드 등 알림 id 가 없는 엔트리는 ACK 로 걷어낸다.
            redisTemplate.opsForStream().acknowledge(key, group, record.getId());
            return;
        }
        try {
            deliveryService.deliver(Long.valueOf(raw.toString()));
            redisTemplate.opsForStream().acknowledge(key, group, record.getId());
        } catch (RuntimeException exception) {
            // ACK 하지 않는다 → PEL 에 남아 리퍼가 재시도한다.
            log.warn("알림 전송 실패, 리퍼 재시도 대상으로 남김: recordId={}", record.getId(), exception);
        }
    }
}
