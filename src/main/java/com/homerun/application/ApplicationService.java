package com.homerun.application;

import com.homerun.application.ApplicationDtos.ApplicationList;
import com.homerun.application.ApplicationDtos.ApplicationView;
import com.homerun.application.ApplicationDtos.CreateRequest;
import com.homerun.application.ApplicationDtos.UpdateRequest;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신청 실행(APP-01). 실제 기관 제출은 하지 않고 준비와 상태만 관리한다(OUT-01-03). */
@Service
public class ApplicationService {

    private final PolicyApplicationRepository applications;
    private final Clock clock;

    public ApplicationService(PolicyApplicationRepository applications, Clock clock) {
        this.applications = applications;
        this.clock = clock;
    }

    /** 신청 건을 만든다(APP-01-02). 같은 계획에서 같은 정책을 두 번 신청하지 않는다. */
    @Transactional
    public ApplicationView create(CreateRequest request) {
        if (applications.existsByPlanIdAndPolicyId(request.planId(), request.policyId())) {
            throw new DuplicateApplicationException(request.policyId());
        }
        return toView(
                applications.save(new PolicyApplication(request.planId(), request.policyId(), request.channel())));
    }

    @Transactional(readOnly = true)
    public ApplicationList list(Long planId) {
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
    public ApplicationView update(Long id, UpdateRequest request) {
        PolicyApplication application =
                applications.findById(id).orElseThrow(() -> new ApplicationNotFoundException(id));

        Instant now = Instant.now(clock);
        application.changeStatus(request.status(), now);

        if (request.status() == ApplicationStatus.REJECTED) {
            if (request.rejectStage() == null) {
                throw new IllegalArgumentException("거절은 어느 단계에서 막혔는지를 함께 기록해야 한다");
            }
            application.recordRejection(request.rejectStage(), request.rejectReasonCode());
        } else if (request.status() == ApplicationStatus.APPROVED) {
            application.recordApproval(request.approvedAmount(), request.approvedRate());
        }
        return toView(application);
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
                application.approvedAmount(),
                application.approvedRate(),
                RejectGuidance.forStage(application.rejectStage()));
    }
}
