package com.homerun.domain.notification.scheduler;

import com.homerun.domain.contract.dto.response.ContractScheduleResponse.Milestone;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.contract.service.ContractScheduleService;
import com.homerun.domain.notification.type.NotificationType;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * 3루 계약 일정(3-5~3-11) 마일스톤 알림. 잔금일이 정해진 계약에서 {@link ContractScheduleService}
 * 가 역산한 마일스톤을 그대로 받아, 오늘이 마일스톤 당일인 건을 후보로 만든다.
 *
 * <p>기존 {@code ContractDeadlineSource}(잔금일 D-7/D-3/D-1 넛지)를 대체한다. 잔금일 당일은
 * 등기부 재대조·잔금/전입 두 마일스톤으로, D-3 심사 확인도 마일스톤으로 커버된다.
 *
 * <p>D-day 이후인 반환보증 가입(3-12)은 잔금 납부 후({@code balancePaidAt} 세팅)라 여기 쿼리에서
 * 빠진다 — 별도 소스로 분리한다. 이 소스는 잔금일 이전·당일 마일스톤만 다룬다.
 */
@Component
public class ContractMilestoneSource implements DeadlineSource {

    /** 오늘 마일스톤을 낳을 수 있는 잔금일 범위. 가장 이른 마일스톤이 D-30이라 today~today+30이면 충분하다. */
    private static final int WINDOW_DAYS = 30;

    private final LeaseContractRepository leaseContractRepository;
    private final PlanRepository planRepository;
    private final ContractScheduleService scheduleService;

    public ContractMilestoneSource(
            LeaseContractRepository leaseContractRepository,
            PlanRepository planRepository,
            ContractScheduleService scheduleService) {
        this.leaseContractRepository = leaseContractRepository;
        this.planRepository = planRepository;
        this.scheduleService = scheduleService;
    }

    @Override
    public List<DeadlineCandidate> collect(LocalDate today) {
        List<LocalDate> window =
                IntStream.rangeClosed(0, WINDOW_DAYS).mapToObj(today::plusDays).toList();
        List<LeaseContract> contracts = leaseContractRepository.findByBalanceDateInAndBalancePaidAtIsNull(window);
        if (contracts.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> memberIdByPlanId = memberIdsByPlan(contracts);

        List<DeadlineCandidate> candidates = new ArrayList<>();
        for (LeaseContract contract : contracts) {
            Long memberId = memberIdByPlanId.get(contract.getPlanId());
            if (memberId == null) {
                continue;
            }
            LocalDate balanceDate = contract.getBalanceDate();
            for (Milestone milestone : scheduleService.milestones(contract)) {
                if (!milestone.dueDate().equals(today)) {
                    continue;
                }
                // 잔금일 이후(반환보증 등)는 이 소스 범위 밖이다. 3-5~3-11만 다룬다.
                if (balanceDate != null && milestone.dueDate().isAfter(balanceDate)) {
                    continue;
                }
                candidates.add(candidate(memberId, contract, balanceDate, milestone));
            }
        }
        return candidates;
    }

    /** 계약마다 findById 를 부르지 않고 planId 를 모아 한 번에 회원 ID를 조회한다. */
    private Map<Long, Long> memberIdsByPlan(List<LeaseContract> contracts) {
        Set<Long> planIds = contracts.stream()
                .map(LeaseContract::getPlanId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Long> memberIdByPlanId = new HashMap<>();
        for (Plan plan : planRepository.findAllById(planIds)) {
            memberIdByPlanId.put(plan.getId(), plan.getMemberId());
        }
        return memberIdByPlanId;
    }

    private DeadlineCandidate candidate(
            Long memberId, LeaseContract contract, LocalDate balanceDate, Milestone milestone) {
        Map<String, String> data = new HashMap<>();
        data.put("contractId", String.valueOf(contract.getId()));
        data.put("planId", String.valueOf(contract.getPlanId()));
        data.put("milestoneCode", milestone.code());
        data.put("dueDate", milestone.dueDate().toString());
        if (balanceDate != null) {
            data.put("balanceDate", balanceDate.toString());
        }
        String dedupKey =
                "CONTRACT_MILESTONE:%d:%s:%s".formatted(contract.getId(), milestone.code(), milestone.dueDate());
        return new DeadlineCandidate(
                memberId,
                NotificationType.CONTRACT_MILESTONE,
                milestone.label(),
                milestone.reason(),
                Map.copyOf(data),
                dedupKey);
    }
}
