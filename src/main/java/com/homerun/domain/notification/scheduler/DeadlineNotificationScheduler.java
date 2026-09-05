package com.homerun.domain.notification.scheduler;

import com.homerun.domain.notification.service.NotificationEventPublisher;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 마감/넛지 알림을 매일 한 번 예약한다. 실제 전송은 프로듀서 → 스트림 → 컨슈머가 담당한다.
 *
 * <p>등록된 모든 {@link DeadlineSource} 를 순회해 후보를 모으고 프로듀서로 발행한다. 발행은
 * dedupKey 로 멱등하므로, 하루에 여러 번 돌거나 재시작해도 같은 알림이 중복 생성되지 않는다.
 */
@Component
public class DeadlineNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeadlineNotificationScheduler.class);

    private final List<DeadlineSource> deadlineSources;
    private final NotificationEventPublisher publisher;
    private final Clock clock;

    public DeadlineNotificationScheduler(
            List<DeadlineSource> deadlineSources, NotificationEventPublisher publisher, Clock clock) {
        this.deadlineSources = deadlineSources;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Scheduled(cron = "${notification.deadline.cron:0 0 9 * * *}", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(clock);
        int published = 0;
        for (DeadlineSource source : deadlineSources) {
            for (DeadlineCandidate candidate : source.collect(today)) {
                if (publisher
                        .publish(
                                candidate.memberId(),
                                candidate.type(),
                                candidate.title(),
                                candidate.body(),
                                candidate.data(),
                                candidate.dedupKey())
                        .isPresent()) {
                    published++;
                }
            }
        }
        log.info("마감/넛지 알림 스캔 완료: sources={}, published={}", deadlineSources.size(), published);
    }
}
