package com.homerun.domain.notification.scheduler;

import com.homerun.domain.application.entity.PolicyApplication;
import com.homerun.domain.application.repository.PolicyApplicationRepository;
import com.homerun.domain.application.type.ApplicationStatus;
import com.homerun.domain.notification.config.NotificationProperties;
import com.homerun.domain.notification.type.NotificationType;
import com.homerun.domain.plan.repository.PlanRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 신청 결과 대기 지연 넛지.
 *
 * <p>정책 신청에는 접수 마감일 데이터가 없다(설계 문서의 확정값 2번 참고). 그래서 마감 대신,
 * 제출된 지(SUBMITTED/SCREENING) {@code staleDays} 일이 지나도 결과가 없는 신청을 넛지한다 —
 * 실데이터({@link PolicyApplication#submittedAt()}) 기반. 진짜 접수 마감 컬럼이 생기면 별도
 * {@code DeadlineSource} 로 추가한다.
 *
 * <p>dedupKey 에 신청 id 만 넣어 한 신청당 한 번만 넛지한다(매일 반복 알림 방지).
 */
@Component
public class ApplicationResultPendingSource implements DeadlineSource {

    private static final List<ApplicationStatus> PENDING_STATUSES =
            List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.SCREENING);

    private final PolicyApplicationRepository policyApplicationRepository;
    private final PlanRepository planRepository;
    private final NotificationProperties properties;
    private final Clock clock;

    public ApplicationResultPendingSource(
            PolicyApplicationRepository policyApplicationRepository,
            PlanRepository planRepository,
            NotificationProperties properties,
            Clock clock) {
        this.policyApplicationRepository = policyApplicationRepository;
        this.planRepository = planRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public List<DeadlineCandidate> collect(LocalDate today) {
        int staleDays = properties.staleApplication().staleDays();
        var threshold = clock.instant().minus(java.time.Duration.ofDays(staleDays));

        List<DeadlineCandidate> candidates = new ArrayList<>();
        for (PolicyApplication application :
                policyApplicationRepository.findByStatusInAndSubmittedAtBefore(PENDING_STATUSES, threshold)) {
            Long memberId = planRepository
                    .findById(application.planId())
                    .map(plan -> plan.getMemberId())
                    .orElse(null);
            if (memberId == null) {
                continue;
            }
            candidates.add(new DeadlineCandidate(
                    memberId,
                    NotificationType.APPLICATION_RESULT_PENDING,
                    "신청 결과가 %d일째 대기 중이에요".formatted(staleDays),
                    "제출한 정책 신청의 결과가 오래 지연되고 있어요. 진행 상황을 확인해 보세요.",
                    Map.of(
                            "applicationId", String.valueOf(application.id()),
                            "status", application.status().name()),
                    "%s:%d".formatted(NotificationType.APPLICATION_RESULT_PENDING.name(), application.id())));
        }
        return candidates;
    }
}
