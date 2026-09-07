package com.homerun.domain.settlement.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.settlement.dto.request.ReturnGuaranteeEnrollmentRequest;
import com.homerun.domain.settlement.dto.response.ReturnGuaranteeEnrollmentResponse;
import com.homerun.domain.settlement.entity.ReturnGuaranteeEnrollment;
import com.homerun.domain.settlement.repository.ReturnGuaranteeEnrollmentRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 반환보증 가입 상태 저장·조회(FR-H1-03). 계획당 1건이라 저장은 덮어쓴다. */
@Service
public class ReturnGuaranteeEnrollmentService {

    private final PlanRepository plans;
    private final ReturnGuaranteeEnrollmentRepository enrollments;

    public ReturnGuaranteeEnrollmentService(PlanRepository plans, ReturnGuaranteeEnrollmentRepository enrollments) {
        this.plans = plans;
        this.enrollments = enrollments;
    }

    @Transactional
    public ReturnGuaranteeEnrollmentResponse save(
            Long memberId, Long planId, ReturnGuaranteeEnrollmentRequest request) {
        ownedPlan(memberId, planId);
        ReturnGuaranteeEnrollment entity =
                enrollments.findByPlanId(planId).orElseGet(() -> new ReturnGuaranteeEnrollment(planId));
        entity.update(request.enrolled(), request.feePaid(), request.enrolledAt());
        return ReturnGuaranteeEnrollmentResponse.from(enrollments.save(entity));
    }

    @Transactional(readOnly = true)
    public ReturnGuaranteeEnrollmentResponse get(Long memberId, Long planId) {
        ownedPlan(memberId, planId);
        return ReturnGuaranteeEnrollmentResponse.from(enrollments
                .findByPlanId(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RETURN_GUARANTEE_NOT_FOUND)));
    }

    private void ownedPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
    }
}
