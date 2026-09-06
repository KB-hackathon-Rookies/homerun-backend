package com.homerun.domain.notification.dto.response;

import com.homerun.domain.notification.entity.Notification;
import com.homerun.domain.notification.type.NotificationStatus;
import com.homerun.domain.notification.type.NotificationType;
import java.time.Instant;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 인박스 알림 한 줄 응답. {@code data} 는 프론트 딥링크용 JSON 객체(없으면 null). */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        JsonNode data,
        NotificationStatus status,
        boolean read,
        Instant createdAt,
        Instant readAt) {

    public static NotificationResponse from(Notification notification, ObjectMapper objectMapper) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                parse(notification.getData(), objectMapper),
                notification.getStatus(),
                notification.getReadAt() != null,
                notification.getCreatedAt(),
                notification.getReadAt());
    }

    private static JsonNode parse(String dataJson, ObjectMapper objectMapper) {
        if (dataJson == null || dataJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(dataJson);
        } catch (Exception exception) {
            return null;
        }
    }
}
