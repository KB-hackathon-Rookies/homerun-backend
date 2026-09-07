package com.homerun.domain.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.notification.type.NotificationType;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostMoveInContractSourceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);

    private final LeaseContractRepository contracts = mock(LeaseContractRepository.class);
    private final PlanRepository plans = mock(PlanRepository.class);
    private final PostMoveInContractSource source = new PostMoveInContractSource(contracts, plans);

    @Test
    void should_remindReturnGuarantee_dayAfterBalancePayment() {
        LeaseContract contract = contract(10L, 100L, null);
        Plan ownerPlan = plan(10L, 7L);
        when(contracts.findByBalancePaidAt(TODAY.minusDays(1))).thenReturn(List.of(contract));
        when(contracts.findByLeaseEndDateIn(any())).thenReturn(List.of());
        when(plans.findAllById(any())).thenReturn(List.of(ownerPlan));

        List<DeadlineCandidate> result = source.collect(TODAY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo(NotificationType.RETURN_GUARANTEE_REMINDER);
        assertThat(result.get(0).dedupKey()).isEqualTo("RETURN_GUARANTEE:100:2026-09-07");
    }

    @Test
    void should_emitRenewalWindow_atSixAndTwoMonths() {
        LeaseContract sixMonths = contract(10L, 100L, TODAY.plusMonths(6));
        LeaseContract twoMonths = contract(10L, 101L, TODAY.plusMonths(2));
        Plan ownerPlan = plan(10L, 7L);
        when(contracts.findByBalancePaidAt(TODAY.minusDays(1))).thenReturn(List.of());
        when(contracts.findByLeaseEndDateIn(any())).thenReturn(List.of(sixMonths, twoMonths));
        when(plans.findAllById(any())).thenReturn(List.of(ownerPlan));

        List<DeadlineCandidate> result = source.collect(TODAY);

        assertThat(result).hasSize(2).allMatch(candidate -> candidate.type() == NotificationType.LEASE_RENEWAL_WINDOW);
        assertThat(result)
                .extracting(DeadlineCandidate::dedupKey)
                .containsExactlyInAnyOrder("LEASE_RENEWAL:100:M6:2027-03-07", "LEASE_RENEWAL:101:M2:2026-11-07");
    }

    private LeaseContract contract(long planId, long id, LocalDate leaseEndDate) {
        LeaseContract contract = mock(LeaseContract.class);
        lenient().when(contract.getPlanId()).thenReturn(planId);
        lenient().when(contract.getId()).thenReturn(id);
        lenient().when(contract.getLeaseEndDate()).thenReturn(leaseEndDate);
        return contract;
    }

    private Plan plan(long id, long memberId) {
        Plan plan = mock(Plan.class);
        when(plan.getId()).thenReturn(id);
        when(plan.getMemberId()).thenReturn(memberId);
        return plan;
    }
}
