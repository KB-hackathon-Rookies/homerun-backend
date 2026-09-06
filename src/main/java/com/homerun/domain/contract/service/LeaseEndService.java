package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.request.LeaseEndRequest;
import com.homerun.domain.contract.dto.response.LeaseEndResponse;
import com.homerun.domain.contract.entity.LeaseEnd;
import com.homerun.domain.contract.repository.LeaseEndRepository;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 갱신·퇴거 결정 저장·조회(FR-H9-01·02). 계획당 1건이라 저장은 덮어쓴다. */
@Service
public class LeaseEndService {

    private final PlanRepository plans;
    private final LeaseEndRepository leaseEnds;
    private final Clock clock;

    public LeaseEndService(PlanRepository plans, LeaseEndRepository leaseEnds, Clock clock) {
        this.plans = plans;
        this.leaseEnds = leaseEnds;
        this.clock = clock;
    }

    @Transactional
    public LeaseEndResponse decide(Long memberId, Long planId, LeaseEndRequest request) {
        ownedPlan(memberId, planId);
        LeaseEnd entity = leaseEnds.findByPlanId(planId).orElseGet(() -> new LeaseEnd(planId));
        entity.decide(request.decision(), request.renewalMethod(), request.noticeSentAt(), Instant.now(clock));
        return LeaseEndResponse.from(leaseEnds.save(entity));
    }

    @Transactional(readOnly = true)
    public LeaseEndResponse get(Long memberId, Long planId) {
        ownedPlan(memberId, planId);
        return LeaseEndResponse.from(
                leaseEnds.findByPlanId(planId).orElseThrow(() -> new BusinessException(ErrorCode.LEASE_END_NOT_FOUND)));
    }

    private void ownedPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
    }
}
