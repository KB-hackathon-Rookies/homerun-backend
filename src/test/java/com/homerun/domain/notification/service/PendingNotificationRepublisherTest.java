package com.homerun.domain.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.notification.config.NotificationProperties;
import com.homerun.domain.notification.entity.Notification;
import com.homerun.domain.notification.repository.NotificationRepository;
import com.homerun.domain.notification.type.NotificationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class PendingNotificationRepublisherTest {

    @Test
    void should_republishOldPendingNotifications() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationEventPublisher publisher = mock(NotificationEventPublisher.class);
        Notification notification = mock(Notification.class);
        when(notification.getId()).thenReturn(11L);
        when(repository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(eq(NotificationStatus.PENDING), any(), any()))
                .thenReturn(List.of(notification));
        when(publisher.enqueueExisting(11L)).thenReturn(true);
        var properties = new NotificationProperties(
                new NotificationProperties.Stream("stream", "group", "worker", 10, Duration.ofSeconds(1)),
                new NotificationProperties.Reaper(Duration.ofMinutes(5), 3, 100),
                new NotificationProperties.Recovery(Duration.ofMinutes(5), 20),
                new NotificationProperties.Deadline(List.of(7, 3, 1)),
                new NotificationProperties.StaleApplication(14));
        Clock clock = Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC);

        new PendingNotificationRepublisher(repository, publisher, properties, clock).republish();

        verify(repository)
                .findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        eq(NotificationStatus.PENDING), eq(Instant.parse("2026-09-06T23:55:00Z")), any(Pageable.class));
        verify(publisher).enqueueExisting(11L);
    }
}
