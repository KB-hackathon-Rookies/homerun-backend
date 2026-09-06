package com.homerun.domain.notification.service;

import com.homerun.domain.notification.dto.response.NotificationResponse;
import com.homerun.domain.notification.entity.Notification;
import com.homerun.domain.notification.repository.NotificationRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 사용자 인박스 조회와 읽음 처리. */
@Service
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationQueryService(
            NotificationRepository notificationRepository, ObjectMapper objectMapper, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(Long memberId, boolean unreadOnly, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<Notification> notifications = unreadOnly
                ? notificationRepository.findByMemberIdAndReadAtIsNullOrderByCreatedAtDesc(memberId, page)
                : notificationRepository.findByMemberIdOrderByCreatedAtDesc(memberId, page);
        return notifications.stream()
                .map(notification -> NotificationResponse.from(notification, objectMapper))
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead(clock.instant());
        return NotificationResponse.from(notification, objectMapper);
    }
}
