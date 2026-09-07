package com.homerun.domain.notification.service;

import com.homerun.domain.notification.config.NotificationProperties;
import com.homerun.domain.notification.repository.NotificationRepository;
import com.homerun.domain.notification.type.NotificationStatus;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** DB 저장 뒤 Redis 발행에 실패한 PENDING 알림을 다시 스트림에 넣는다. 전달은 멱등 처리된다. */
@Component
public class PendingNotificationRepublisher {

    private static final Logger log = LoggerFactory.getLogger(PendingNotificationRepublisher.class);

    private final NotificationRepository notifications;
    private final NotificationEventPublisher publisher;
    private final NotificationProperties properties;
    private final Clock clock;

    public PendingNotificationRepublisher(
            NotificationRepository notifications,
            NotificationEventPublisher publisher,
            NotificationProperties properties,
            Clock clock) {
        this.notifications = notifications;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${notification.recovery.fixed-delay:60s}")
    public void republish() {
        var recovery = properties.recovery();
        var pending = notifications.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                NotificationStatus.PENDING,
                clock.instant().minus(recovery.minAge()),
                PageRequest.of(0, recovery.batchSize()));
        long published = pending.stream()
                .filter(notification -> publisher.enqueueExisting(notification.getId()))
                .count();
        if (!pending.isEmpty()) {
            log.info("PENDING 알림 복구: candidates={}, published={}", pending.size(), published);
        }
    }
}
