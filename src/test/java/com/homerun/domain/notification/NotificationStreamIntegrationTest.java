package com.homerun.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.notification.entity.Notification;
import com.homerun.domain.notification.repository.NotificationRepository;
import com.homerun.domain.notification.service.NotificationEventPublisher;
import com.homerun.domain.notification.service.NotificationStreamConsumer;
import com.homerun.domain.notification.type.NotificationStatus;
import com.homerun.domain.notification.type.NotificationType;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/**
 * 진짜 Postgres + Redis 로 알림 스트림 전 구간(프로듀서 → 스트림 → 컨슈머 → SENT)과 dedup 을 확인한다.
 * FCM 은 기본 NoOpFcmSender(fcm.enabled=false)라 전송은 성공으로 처리된다.
 *
 * <p>클래스 레벨 @Transactional 을 쓰지 않는다 — 프로듀서가 커밋 이후(afterCommit)에 XADD 하므로,
 * 롤백되는 테스트 트랜잭션 안에서는 스트림에 아무것도 실리지 않기 때문이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NotificationStreamIntegrationTest {

    private final NotificationEventPublisher publisher;
    private final NotificationStreamConsumer consumer;
    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;

    NotificationStreamIntegrationTest(
            @Autowired NotificationEventPublisher publisher,
            @Autowired NotificationStreamConsumer consumer,
            @Autowired NotificationRepository notificationRepository,
            @Autowired MemberRepository memberRepository) {
        this.publisher = publisher;
        this.consumer = consumer;
        this.notificationRepository = notificationRepository;
        this.memberRepository = memberRepository;
    }

    @Test
    @DisplayName("발행하면 컨슈머가 스트림에서 읽어 SENT 로 만든다")
    void should_deliverToSent_when_published() throws InterruptedException {
        Member member = memberRepository.save(
                Member.create(AuthProvider.KAKAO, "noti-deliver-" + System.nanoTime(), null, "tester"));

        Long notificationId = publisher
                .publish(member.getId(), NotificationType.CONTRACT_DEADLINE, "제목", "본문", Map.of("k", "v"), null)
                .orElseThrow();

        NotificationStatus status = awaitStatus(notificationId);
        assertThat(status).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("같은 dedupKey 로 두 번 발행하면 한 건만 만들어진다")
    void should_createOnce_when_sameDedupKey() {
        Member member = memberRepository.save(
                Member.create(AuthProvider.KAKAO, "noti-dedup-" + System.nanoTime(), null, "tester"));
        String dedupKey = "CONTRACT_DEADLINE:dedup-" + System.nanoTime();

        Optional<Long> first =
                publisher.publish(member.getId(), NotificationType.CONTRACT_DEADLINE, "제목", "본문", null, dedupKey);
        Optional<Long> second =
                publisher.publish(member.getId(), NotificationType.CONTRACT_DEADLINE, "제목", "본문", null, dedupKey);

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        assertThat(notificationRepository.findByMemberIdOrderByCreatedAtDesc(member.getId(), PageRequest.of(0, 10)))
                .hasSize(1);
    }

    private NotificationStatus awaitStatus(Long notificationId) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            consumer.poll();
            Notification notification =
                    notificationRepository.findById(notificationId).orElseThrow();
            if (notification.getStatus() == NotificationStatus.SENT) {
                return notification.getStatus();
            }
            Thread.sleep(100);
        }
        return notificationRepository.findById(notificationId).orElseThrow().getStatus();
    }
}
