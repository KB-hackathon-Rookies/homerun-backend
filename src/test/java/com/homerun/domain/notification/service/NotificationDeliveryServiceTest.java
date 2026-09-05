package com.homerun.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.homerun.domain.notification.entity.DeviceToken;
import com.homerun.domain.notification.entity.Notification;
import com.homerun.domain.notification.repository.DeviceTokenRepository;
import com.homerun.domain.notification.repository.NotificationRepository;
import com.homerun.domain.notification.type.DevicePlatform;
import com.homerun.domain.notification.type.NotificationStatus;
import com.homerun.domain.notification.type.NotificationType;
import com.homerun.global.external.fcm.FcmSendResult;
import com.homerun.global.external.fcm.FcmSender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-06T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private FcmSender fcmSender;

    private NotificationDeliveryService service() {
        return new NotificationDeliveryService(
                notificationRepository, deviceTokenRepository, fcmSender, new ObjectMapper(), CLOCK);
    }

    private Notification pending() {
        return Notification.pending(7L, NotificationType.CONTRACT_DEADLINE, "제목", "본문", null, "k", Instant.now(CLOCK));
    }

    @Test
    @DisplayName("전송 성공 시 SENT 로 바꾸고 ACK 가능(true)을 돌려준다")
    void should_markSent_when_deliverSucceeds() {
        Notification notification = pending();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(deviceTokenRepository.findByMemberId(7L))
                .thenReturn(List.of(new DeviceToken(7L, "tok", DevicePlatform.ANDROID, Instant.now(CLOCK))));
        when(fcmSender.send(anyList(), any(), any(), any())).thenReturn(new FcmSendResult(1, 0));

        boolean ack = service().deliver(1L);

        assertThat(ack).isTrue();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("수신 토큰이 없어도 인박스에는 남으므로 SENT 로 처리한다")
    void should_markSent_when_noTokens() {
        Notification notification = pending();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(deviceTokenRepository.findByMemberId(7L)).thenReturn(List.of());
        when(fcmSender.send(anyList(), any(), any(), any())).thenReturn(FcmSendResult.noRecipients());

        service().deliver(1L);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("FCM 전송이 실패하면 예외를 전파하고 상태는 PENDING 으로 남긴다(ACK 금지)")
    void should_notMarkSent_when_fcmThrows() {
        Notification notification = pending();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(deviceTokenRepository.findByMemberId(7L)).thenReturn(List.of());
        when(fcmSender.send(anyList(), any(), any(), any())).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service().deliver(1L)).isInstanceOf(RuntimeException.class);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("재시도 상한을 넘으면 FAILED 로 확정하고 드롭(true)")
    void should_markFailed_when_retriesExhausted() {
        Notification notification = pending(); // retryCount = 0
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        boolean drop = service().exhaustedOrPrepareRetry(1L, 0); // 상한 0 → 즉시 초과

        assertThat(drop).isTrue();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("상한 이내면 재시도 카운트를 1 올리고 재시도 진행(false)")
    void should_incrementRetry_when_belowLimit() {
        Notification notification = pending();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        boolean drop = service().exhaustedOrPrepareRetry(1L, 3);

        assertThat(drop).isFalse();
        assertThat(notification.getRetryCount()).isEqualTo(1);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }
}
