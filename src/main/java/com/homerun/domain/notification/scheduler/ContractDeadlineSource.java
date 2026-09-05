package com.homerun.domain.notification.scheduler;

import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.notification.config.NotificationProperties;
import com.homerun.domain.notification.type.NotificationType;
import com.homerun.domain.plan.repository.PlanRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 계약 잔금일 마감 임박 알림. 실데이터({@link LeaseContract#getBalanceDate()}) 기반.
 *
 * <p>오늘 기준 D-7·D-3·D-1(설정값)에 해당하는, 아직 잔금을 치르지 않은 계약을 찾아 후보로 만든다.
 * dedupKey 에 D-N 을 넣어 각 시점마다 1건씩만 나가게 한다.
 */
@Component
public class ContractDeadlineSource implements DeadlineSource {

    private final LeaseContractRepository leaseContractRepository;
    private final PlanRepository planRepository;
    private final NotificationProperties properties;

    public ContractDeadlineSource(
            LeaseContractRepository leaseContractRepository,
            PlanRepository planRepository,
            NotificationProperties properties) {
        this.leaseContractRepository = leaseContractRepository;
        this.planRepository = planRepository;
        this.properties = properties;
    }

    @Override
    public List<DeadlineCandidate> collect(LocalDate today) {
        List<Integer> offsets = properties.deadline().offsetDays();
        List<LocalDate> targetDates = offsets.stream().map(today::plusDays).toList();

        List<DeadlineCandidate> candidates = new ArrayList<>();
        for (LeaseContract contract : leaseContractRepository.findByBalanceDateInAndBalancePaidAtIsNull(targetDates)) {
            Long memberId = planRepository
                    .findById(contract.getPlanId())
                    .map(plan -> plan.getMemberId())
                    .orElse(null);
            if (memberId == null) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(today, contract.getBalanceDate());
            candidates.add(new DeadlineCandidate(
                    memberId,
                    NotificationType.CONTRACT_DEADLINE,
                    "잔금일이 %d일 남았어요".formatted(daysLeft),
                    "계약 잔금일(%s) 전에 대출 실행과 서류를 마무리하세요.".formatted(contract.getBalanceDate()),
                    Map.of(
                            "contractId", String.valueOf(contract.getId()),
                            "balanceDate", contract.getBalanceDate().toString(),
                            "daysLeft", String.valueOf(daysLeft)),
                    "%s:%d:%s:D-%d"
                            .formatted(
                                    NotificationType.CONTRACT_DEADLINE.name(),
                                    contract.getId(),
                                    contract.getBalanceDate(),
                                    daysLeft)));
        }
        return candidates;
    }
}
