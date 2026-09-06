package com.homerun.domain.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.contract.service.ContractScheduleService;
import com.homerun.domain.contract.type.LoanProductKind;
import com.homerun.domain.notification.type.NotificationType;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractMilestoneSourceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 7); // 월요일 — 은행예약 주말 보정 영향 없음

    @Mock
    private LeaseContractRepository leaseContractRepository;

    @Mock
    private PlanRepository planRepository;

    private final Clock clock =
            Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));

    private ContractMilestoneSource source() {
        ContractScheduleService scheduleService =
                new ContractScheduleService(planRepository, leaseContractRepository, clock);
        return new ContractMilestoneSource(leaseContractRepository, planRepository, scheduleService);
    }

    private LeaseContract contract(long id, long planId, LocalDate balanceDate, LoanProductKind kind) {
        LeaseContract contract = mock(LeaseContract.class);
        lenient().when(contract.getId()).thenReturn(id);
        lenient().when(contract.getPlanId()).thenReturn(planId);
        lenient().when(contract.getBalanceDate()).thenReturn(balanceDate);
        lenient().when(contract.getLoanProductKind()).thenReturn(kind);
        return contract;
    }

    private void plansExist(long planId, long memberId) {
        Plan plan = mock(Plan.class);
        lenient().when(plan.getId()).thenReturn(planId);
        lenient().when(plan.getMemberId()).thenReturn(memberId);
        when(planRepository.findAllById(any())).thenReturn(List.of(plan));
    }

    @Test
    @DisplayName("마일스톤 당일이면 그 마일스톤 1건을 후보로 만든다(D-21 은행 예약)")
    void should_emit_milestone_on_its_due_day() {
        // 잔금일 = today+21 -> 은행 방문 예약(D-21)이 오늘
        LeaseContract contract = contract(100L, 10L, TODAY.plusDays(21), LoanProductKind.FUND_GENERAL);
        when(leaseContractRepository.findByBalanceDateInAndBalancePaidAtIsNull(any()))
                .thenReturn(List.of(contract));
        plansExist(10L, 55L);

        List<DeadlineCandidate> candidates = source().collect(TODAY);

        assertThat(candidates).hasSize(1);
        DeadlineCandidate candidate = candidates.get(0);
        assertThat(candidate.memberId()).isEqualTo(55L);
        assertThat(candidate.type()).isEqualTo(NotificationType.CONTRACT_MILESTONE);
        assertThat(candidate.dedupKey()).isEqualTo("CONTRACT_MILESTONE:100:BANK_RESERVATION:" + TODAY);
        assertThat(candidate.data())
                .containsEntry("contractId", "100")
                .containsEntry("planId", "10")
                .containsEntry("milestoneCode", "BANK_RESERVATION")
                .containsEntry("dueDate", TODAY.toString())
                .containsEntry("balanceDate", TODAY.plusDays(21).toString());
    }

    @Test
    @DisplayName("잔금일 당일에는 등기부 재대조·잔금/전입 두 건이 나온다")
    void should_emit_two_candidates_on_balance_day() {
        LeaseContract contract = contract(100L, 10L, TODAY, LoanProductKind.FUND_GENERAL);
        when(leaseContractRepository.findByBalanceDateInAndBalancePaidAtIsNull(any()))
                .thenReturn(List.of(contract));
        plansExist(10L, 55L);

        List<DeadlineCandidate> candidates = source().collect(TODAY);

        assertThat(candidates)
                .extracting(c -> c.data().get("milestoneCode"))
                .containsExactlyInAnyOrder("REGISTRY_RECHECK", "BALANCE_AND_MOVE_IN");
    }

    @Test
    @DisplayName("청년 버팀목이면 D-30 회사 서류 마일스톤이 당일에 나온다")
    void should_emit_company_documents_only_for_youth() {
        LeaseContract contract = contract(100L, 10L, TODAY.plusDays(30), LoanProductKind.FUND_YOUTH);
        when(leaseContractRepository.findByBalanceDateInAndBalancePaidAtIsNull(any()))
                .thenReturn(List.of(contract));
        plansExist(10L, 55L);

        List<DeadlineCandidate> candidates = source().collect(TODAY);

        assertThat(candidates).extracting(c -> c.data().get("milestoneCode")).containsExactly("COMPANY_DOCUMENTS");
    }

    @Test
    @DisplayName("청년 버팀목이 아니면 D-30에는 아무 마일스톤도 없다")
    void should_skip_company_documents_when_not_youth() {
        LeaseContract contract = contract(100L, 10L, TODAY.plusDays(30), LoanProductKind.FUND_GENERAL);
        when(leaseContractRepository.findByBalanceDateInAndBalancePaidAtIsNull(any()))
                .thenReturn(List.of(contract));

        assertThat(source().collect(TODAY)).isEmpty();
    }

    @Test
    @DisplayName("오늘이 어떤 마일스톤에도 안 걸리면 후보가 없다")
    void should_return_empty_when_no_milestone_today() {
        LeaseContract contract = contract(100L, 10L, TODAY.plusDays(15), LoanProductKind.FUND_GENERAL);
        when(leaseContractRepository.findByBalanceDateInAndBalancePaidAtIsNull(any()))
                .thenReturn(List.of(contract));

        assertThat(source().collect(TODAY)).isEmpty();
    }

    @Test
    @DisplayName("회원을 못 찾은 계약(계획 미상)은 제외한다")
    void should_skip_when_member_missing() {
        LeaseContract known = contract(100L, 10L, TODAY.plusDays(21), LoanProductKind.FUND_GENERAL);
        LeaseContract orphan = contract(200L, 20L, TODAY.plusDays(21), LoanProductKind.FUND_GENERAL);
        when(leaseContractRepository.findByBalanceDateInAndBalancePaidAtIsNull(any()))
                .thenReturn(List.of(known, orphan));
        // 계획 10만 회원이 있고 20은 없음
        plansExist(10L, 55L);

        List<DeadlineCandidate> candidates = source().collect(TODAY);

        assertThat(candidates).extracting(DeadlineCandidate::memberId).containsExactly(55L);
    }

    @Test
    @DisplayName("조회 범위는 today ~ today+30 (31일) 이다")
    void should_query_thirty_one_days_window() {
        when(leaseContractRepository.findByBalanceDateInAndBalancePaidAtIsNull(any()))
                .thenReturn(List.of());

        source().collect(TODAY);

        var captor = org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        org.mockito.Mockito.verify(leaseContractRepository).findByBalanceDateInAndBalancePaidAtIsNull(captor.capture());
        assertThat(captor.getValue())
                .hasSize(31)
                .contains(TODAY, TODAY.plusDays(30))
                .doesNotContain(TODAY.minusDays(1), TODAY.plusDays(31));
    }
}
