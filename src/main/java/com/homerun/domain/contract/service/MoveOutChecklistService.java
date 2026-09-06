package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.MoveOutChecklistResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 퇴거 체크리스트 조회(FR-H10-04). 소유권 확인 후 매물 유형·반환보증 여부로 항목을 구성한다. */
@Service
public class MoveOutChecklistService {

    private final PlanRepository plans;
    private final MoveOutChecklistAdvisor advisor;

    public MoveOutChecklistService(PlanRepository plans, MoveOutChecklistAdvisor advisor) {
        this.plans = plans;
        this.advisor = advisor;
    }

    @Transactional(readOnly = true)
    public MoveOutChecklistResponse guide(
            Long memberId, Long planId, boolean aptOrOfficetel, boolean hasReturnGuarantee) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return advisor.guide(aptOrOfficetel, hasReturnGuarantee);
    }
}
