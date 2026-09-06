package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.MoveOutScheduleResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 퇴거 일정 조회(FR-H10-01). 소유권 확인 후 이사일로 일정을 구성한다. */
@Service
public class MoveOutScheduleService {

    private final PlanRepository plans;
    private final MoveOutScheduleAdvisor advisor;

    public MoveOutScheduleService(PlanRepository plans, MoveOutScheduleAdvisor advisor) {
        this.plans = plans;
        this.advisor = advisor;
    }

    @Transactional(readOnly = true)
    public MoveOutScheduleResponse forPlan(Long memberId, Long planId, LocalDate moveOutDate) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return advisor.build(moveOutDate);
    }
}
