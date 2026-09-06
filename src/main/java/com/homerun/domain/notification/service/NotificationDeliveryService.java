package com.homerun.domain.notification.service;

import com.homerun.domain.notification.entity.Notification;
import com.homerun.domain.notification.repository.DeviceTokenRepository;
import com.homerun.domain.notification.repository.NotificationRepository;
import com.homerun.domain.notification.type.NotificationStatus;
import com.homerun.global.external.fcm.FcmSender;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 알림 한 건을 실제로 FCM 으로 내보내고 상태를 갱신한다. 라이브 컨슈머와 재처리 리퍼가 공유한다.
 *
 * <p>FCM 전송이 실패하면 예외가 그대로 전파된다. 호출자는 이때 스트림 엔트리를 ACK 하지 않고
 * 리퍼가 재시도하게 둔다. 전송에 성공하거나(수신 토큰이 없어 인박스에만 남는 경우 포함) 이미
 * 처리된 알림이면 true 를 돌려주어 호출자가 ACK 하도록 한다.
 */
@Service
public class NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);
    private static final TypeReference<Map<String, String>> DATA_TYPE = new TypeReference<>() {};

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final FcmSender fcmSender;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationDeliveryService(
            NotificationRepository notificationRepository,
            DeviceTokenRepository deviceTokenRepository,
            FcmSender fcmSender,
            ObjectMapper objectMapper,
            Clock clock) {
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.fcmSender = fcmSender;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 알림을 전송한다.
     *
     * @return ACK 해도 되는지 여부. 이미 없거나 처리된 알림, 전송 성공 시 true.
     * @throws RuntimeException FCM 전송 실패 시(호출자는 ACK 하지 않는다).
     */
    @Transactional
    public boolean deliver(Long notificationId) {
        Notification notification =
                notificationRepository.findById(notificationId).orElse(null);
        if (notification == null) {
            // 삭제된 알림. 스트림에서 걷어낸다.
            return true;
        }
        if (notification.getStatus() == NotificationStatus.SENT
                || notification.getStatus() == NotificationStatus.READ) {
            // 멱등: 이미 보냈다.
            return true;
        }
        List<String> tokens = deviceTokenRepository.findByMemberId(notification.getMemberId()).stream()
                .map(token -> token.getToken())
                .toList();
        // 전송 실패는 예외로 던져져 여기서 잡지 않는다(호출자가 ACK 하지 않도록).
        fcmSender.send(tokens, notification.getTitle(), notification.getBody(), parseData(notification.getData()));
        notification.markSent(clock.instant());
        log.debug("알림 전송 완료: id={}, recipients={}", notificationId, tokens.size());
        return true;
    }

    /**
     * 리퍼가 회수한 엔트리의 재시도 가부를 판정한다.
     *
     * @return true 면 더 시도하지 말고 ACK(드롭)하라는 뜻. 이미 없거나 처리됐거나 상한 초과 시 FAILED 로 확정한다.
     *     false 면 재시도 카운트를 1 올렸으니 {@link #deliver(Long)} 를 다시 시도하라는 뜻.
     */
    @Transactional
    public boolean exhaustedOrPrepareRetry(Long notificationId, int maxRetries) {
        Notification notification =
                notificationRepository.findById(notificationId).orElse(null);
        if (notification == null
                || notification.getStatus() == NotificationStatus.SENT
                || notification.getStatus() == NotificationStatus.READ
                || notification.getStatus() == NotificationStatus.FAILED) {
            return true;
        }
        if (notification.getRetryCount() >= maxRetries) {
            notification.markFailed();
            log.warn("알림 재시도 상한 초과, FAILED 확정: id={}, retries={}", notificationId, notification.getRetryCount());
            return true;
        }
        notification.increaseRetryCount();
        return false;
    }

    private Map<String, String> parseData(String dataJson) {
        if (dataJson == null || dataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(dataJson, DATA_TYPE);
        } catch (Exception exception) {
            log.warn("알림 data 파싱 실패, 빈 값으로 전송: {}", dataJson);
            return Map.of();
        }
    }
}
