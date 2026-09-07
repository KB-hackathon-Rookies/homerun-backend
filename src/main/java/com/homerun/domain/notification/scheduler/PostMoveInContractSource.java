package com.homerun.domain.notification.scheduler;

import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.notification.type.NotificationType;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 입주 후 반환보증 확인과 계약 종료 갱신 의사표시 시점을 알린다. */
@Component
public class PostMoveInContractSource implements DeadlineSource {

    private final LeaseContractRepository contracts;
    private final PlanRepository plans;

    public PostMoveInContractSource(LeaseContractRepository contracts, PlanRepository plans) {
        this.contracts = contracts;
        this.plans = plans;
    }

    @Override
    public List<DeadlineCandidate> collect(LocalDate today) {
        List<LeaseContract> guaranteeTargets = contracts.findByBalancePaidAt(today.minusDays(1));
        List<LeaseContract> renewalTargets =
                contracts.findByLeaseEndDateIn(List.of(today.plusMonths(6), today.plusMonths(2)));
        Set<LeaseContract> all = new LinkedHashSet<>();
        all.addAll(guaranteeTargets);
        all.addAll(renewalTargets);
        Map<Long, Plan> planById =
                plans.findAllById(all.stream().map(LeaseContract::getPlanId).collect(Collectors.toSet())).stream()
                        .collect(Collectors.toMap(Plan::getId, Function.identity()));

        List<DeadlineCandidate> candidates = new ArrayList<>();
        guaranteeTargets.forEach(contract -> addGuarantee(candidates, contract, planById, today));
        renewalTargets.forEach(contract -> addRenewal(candidates, contract, planById, today));
        return candidates;
    }

    private void addGuarantee(
            List<DeadlineCandidate> candidates, LeaseContract contract, Map<Long, Plan> planById, LocalDate today) {
        Plan plan = planById.get(contract.getPlanId());
        if (plan == null) {
            return;
        }
        candidates.add(new DeadlineCandidate(
                plan.getMemberId(),
                NotificationType.RETURN_GUARANTEE_REMINDER,
                "전세보증금 반환보증을 확인해 주세요",
                "잔금과 전입을 마쳤다면 반환보증 가입 대상과 기한을 확인할 차례예요.",
                Map.of("planId", String.valueOf(plan.getId()), "contractId", String.valueOf(contract.getId())),
                "RETURN_GUARANTEE:%d:%s".formatted(contract.getId(), today)));
    }

    private void addRenewal(
            List<DeadlineCandidate> candidates, LeaseContract contract, Map<Long, Plan> planById, LocalDate today) {
        Plan plan = planById.get(contract.getPlanId());
        if (plan == null || contract.getLeaseEndDate() == null) {
            return;
        }
        long months = contract.getLeaseEndDate().equals(today.plusMonths(6)) ? 6 : 2;
        String title = months == 6 ? "갱신·퇴거 결정 기간이 시작됐어요" : "갱신·퇴거 의사를 지금 알려야 해요";
        candidates.add(new DeadlineCandidate(
                plan.getMemberId(),
                NotificationType.LEASE_RENEWAL_WINDOW,
                title,
                "계약 종료 %d개월 전입니다. 임대인에게 의사를 기록이 남는 방식으로 전달해 주세요.".formatted(months),
                Map.of(
                        "planId", String.valueOf(plan.getId()),
                        "contractId", String.valueOf(contract.getId()),
                        "leaseEndDate", contract.getLeaseEndDate().toString()),
                "LEASE_RENEWAL:%d:M%d:%s".formatted(contract.getId(), months, contract.getLeaseEndDate())));
    }
}
