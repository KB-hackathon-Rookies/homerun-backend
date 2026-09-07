package com.homerun.domain.notification.repository;

import com.homerun.domain.notification.entity.Notification;
import com.homerun.domain.notification.type.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndMemberId(Long id, Long memberId);

    List<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    List<Notification> findByMemberIdAndReadAtIsNullOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    boolean existsByDedupKey(String dedupKey);

    List<Notification> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            NotificationStatus status, Instant createdBefore, Pageable pageable);
}
