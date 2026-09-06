package com.homerun.domain.application.service;

import com.homerun.domain.application.dto.ApplicationDtos.ApplicationList;
import com.homerun.domain.application.dto.ApplicationDtos.ApplicationView;
import com.homerun.domain.application.dto.ApplicationDtos.CreateRequest;
import com.homerun.domain.application.dto.ApplicationDtos.UpdateRequest;
import com.homerun.domain.application.entity.PolicyApplication;
import com.homerun.domain.application.repository.PolicyApplicationRepository;
import com.homerun.domain.application.type.ApplicationStatus;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신청 실행(APP-01). 실제 기관 제출은 하지 않고 준비와 상태만 관리한다(OUT-01-03). */
@Service
public class ApplicationService {

    /** V11 에서 만든 (plan_id, policy_id) 유니크 제약 이름. */
    private static final String DUPLICATE_CONSTRAINT = "uq_application_plan_policy";

    private final PolicyApplicationRepository applications;
    private final PlanRepository plans;
    private final Clock clock;

    public ApplicationService(PolicyApplicationRepository applications, PlanRepository plans, Clock clock) {
        this.applications = applications;
        this.plans = plans;
        this.clock = clock;
    }

    /** 남의 계획을 건드리지 못하게 한다(SEC-01-04). */
    private void verifyOwner(Long memberId, Long planId) {
        plans.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND))
                .verifyOwner(memberId);
    }

    /** 신청 건을 만든다(APP-01-02). 같은 계획에서 같은 정책을 두 번 신청하지 않는다. */
    @Transactional
    public ApplicationView create(Long memberId, Long planId, CreateRequest request) {
        verifyOwner(memberId, planId);
        // exists 후 save 는 동시 요청에서 둘 다 통과한다. DB 유니크 제약이 최종 방어선이고
        // 여기서는 흔한 경우를 먼저 걸러 사용자에게 바로 알려준다.
        if (applications.existsByPlanIdAndPolicyId(planId, request.policyId())) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_EXISTS);
        }
        try {
            return toView(
                    applications.saveAndFlush(new PolicyApplication(planId, request.policyId(), request.channel())));
        } catch (DataIntegrityViolationException e) {
            // 무결성 오류를 전부 중복으로 바꾸면, 없는 정책을 신청한 사람에게도
            // "이미 신청한 정책"이라고 답하게 된다. 제약 이름으로 갈라낸다.
            if (violates(e, DUPLICATE_CONSTRAINT)) {
                throw new BusinessException(ErrorCode.APPLICATION_ALREADY_EXISTS, e);
            }
            throw new BusinessException(ErrorCode.POLICY_NOT_FOUND, e);
        }
    }

    @Transactional(readOnly = true)
    public ApplicationList list(Long memberId, Long planId) {
        verifyOwner(memberId, planId);
        return new ApplicationList(applications.findByPlanIdOrderByIdDesc(planId).stream()
                .map(ApplicationService::toView)
                .toList());
    }

    /**
     * 진행 상태와 결과를 기록한다(APP-01-05, APP-01-06, APP-01-07).
     *
     * <p>거절이면 어느 단계에서 막혔는지를 반드시 받는다. 이 값이 없으면 사용자에게 무엇을
     * 하라고 말할 수가 없다.
     */
    @Transactional
    public ApplicationView update(Long memberId, Long planId, Long id, UpdateRequest request) {
        verifyOwner(memberId, planId);
        // 경로의 planId 소유권만 보면 남의 신청 건 ID 를 끼워 넣어 상태를 바꿀 수 있다.
        // 조회한 건이 그 계획에 속하는지까지 확인한다.
        PolicyApplication application = applications
                .findByIdAndPlanId(id, planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        Instant now = Instant.now(clock);
        application.changeStatus(request.status(), now);

        if (request.status() == ApplicationStatus.REJECTED) {
            if (request.rejectStage() == null) {
                throw new BusinessException(ErrorCode.REJECT_STAGE_REQUIRED);
            }
            application.recordRejection(request.rejectStage(), request.rejectReasonCode(), request.rejectionEvidence());
        } else if (request.status() == ApplicationStatus.APPROVED) {
            application.recordApproval(request.approvedAmount(), request.approvedRate());
        }
        return toView(application);
    }

    private boolean violates(DataIntegrityViolationException e, String constraintName) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(constraintName)) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    private static ApplicationView toView(PolicyApplication application) {
        return new ApplicationView(
                application.id(),
                application.planId(),
                application.policyId(),
                application.channel(),
                application.status(),
                application.submittedAt(),
                application.resultAt(),
                application.rejectStage(),
                application.rejectReasonCode(),
                application.rejectionEvidence(),
                application.approvedAmount(),
                application.approvedRate(),
                RejectGuidance.forStage(application.rejectStage()));
    }
}
