package com.homerun.domain.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.notification.config.NotificationProperties;
import com.homerun.domain.notification.type.NotificationType;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractDeadlineSourceTest {

    @Mock
    private LeaseContractRepository leaseContractRepository;

    @Mock
    private PlanRepository planRepository;

    private NotificationProperties properties() {
        return new NotificationProperties(
                new NotificationProperties.Stream("notifications:stream", "g", "c", 10, Duration.ofSeconds(1)),
                new NotificationProperties.Reaper(Duration.ofMinutes(5), 3, 100),
                new NotificationProperties.Deadline(List.of(7, 3, 1)),
                new NotificationProperties.StaleApplication(14));
    }

    @Test
    @DisplayName("잔금일 D-3 계약이면 memberId·타입·dedupKey·daysLeft 가 채워진 후보를 만든다")
    void should_buildCandidate_when_balanceDateWithinOffset() {
        LocalDate today = LocalDate.of(2026, 9, 6);
        LeaseContract contract = mock(LeaseContract.class);
        when(contract.getId()).thenReturn(100L);
        when(contract.getPlanId()).thenReturn(10L);
        when(contract.getBalanceDate()).thenReturn(today.plusDays(3));
        when(leaseContractRepository.findByBalanceDateInAndBalancePaidAtIsNull(any()))
                .thenReturn(List.of(contract));
        Plan plan = mock(Plan.class);
        when(plan.getMemberId()).thenReturn(55L);
        when(planRepository.findById(10L)).thenReturn(Optional.of(plan));

        ContractDeadlineSource source =
                new ContractDeadlineSource(leaseContractRepository, planRepository, properties());

        List<DeadlineCandidate> candidates = source.collect(today);

        assertThat(candidates).hasSize(1);
        DeadlineCandidate candidate = candidates.get(0);
        assertThat(candidate.memberId()).isEqualTo(55L);
        assertThat(candidate.type()).isEqualTo(NotificationType.CONTRACT_DEADLINE);
        assertThat(candidate.dedupKey()).isEqualTo("CONTRACT_DEADLINE:100:2026-09-09:D-3");
        assertThat(candidate.data()).containsEntry("daysLeft", "3");
    }

    @Test
    @DisplayName("계획을 못 찾으면(회원 미상) 후보에서 제외한다")
    void should_skip_when_planMissing() {
        LocalDate today = LocalDate.of(2026, 9, 6);
        LeaseContract contract = mock(LeaseContract.class);
        when(contract.getPlanId()).thenReturn(10L);
        when(leaseContractRepository.findByBalanceDateInAndBalancePaidAtIsNull(any()))
                .thenReturn(List.of(contract));
        when(planRepository.findById(10L)).thenReturn(Optional.empty());

        ContractDeadlineSource source =
                new ContractDeadlineSource(leaseContractRepository, planRepository, properties());

        assertThat(source.collect(today)).isEmpty();
    }
}
