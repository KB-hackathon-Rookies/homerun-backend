package com.homerun.domain.property.service;

import com.homerun.domain.plan.dto.request.CompletePlanStepRequest;
import com.homerun.domain.plan.dto.response.PlanProgressResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.service.PlanService;
import com.homerun.domain.plan.type.PlanGate;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.property.dto.request.SecondBaseCompleteRequest;
import com.homerun.domain.property.dto.response.BankConsultationResponse;
import com.homerun.domain.property.dto.response.PropertyDecisionResponse;
import com.homerun.domain.property.dto.response.SecondBaseCompleteResponse;
import com.homerun.domain.property.dto.response.SecondBaseResultResponse;
import com.homerun.domain.property.dto.response.SecondBaseResultSnapshot;
import com.homerun.domain.property.entity.PropertyDecision;
import com.homerun.domain.property.entity.SecondBaseSubmission;
import com.homerun.domain.property.repository.PropertyDecisionRepository;
import com.homerun.domain.property.repository.SecondBaseSubmissionRepository;
import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.ConsultationResultStatus;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class SecondBaseCompletionService {

    private final PlanRepository plans;
    private final PropertyDecisionRepository decisions;
    private final SecondBaseSubmissionRepository submissions;
    private final PropertyDecisionService decisionService;
    private final PlanService planService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SecondBaseCompletionService(
            PlanRepository plans,
            PropertyDecisionRepository decisions,
            SecondBaseSubmissionRepository submissions,
            PropertyDecisionService decisionService,
            PlanService planService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.plans = plans;
        this.decisions = decisions;
        this.submissions = submissions;
        this.decisionService = decisionService;
        this.planService = planService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public SecondBaseCompleteResponse complete(Long memberId, Long planId, SecondBaseCompleteRequest request) {
        Plan plan = plans.findByIdForUpdate(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        plan.verifyRuleVersion(request.ruleVersion());

        PropertyDecision decision = decisions
                .findByPlanIdForUpdate(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPERTY_DECISION_NOT_FOUND));
        if (decision.getRevision() != request.expectedDecisionRevision()) {
            throw new BusinessException(ErrorCode.SECOND_BASE_DECISION_REVISION_MISMATCH);
        }

        SecondBaseSubmission previous = submissions
                .findByPlanIdAndDecisionRevision(planId, decision.getRevision())
                .orElse(null);
        if (previous != null) {
            SecondBaseResultSnapshot snapshot = snapshot(previous);
            return response(true, snapshot, replayProgress(memberId, planId, plan, request.ruleVersion()));
        }

        PropertyDecisionResponse selected = decisionService.getDecision(memberId, planId);
        requireFinalTerms(selected.consultation());
        planService.completeStep(
                memberId,
                planId,
                PlanGate.SECOND_POLICY_SELECTION.code(),
                new CompletePlanStepRequest(request.ruleVersion()));
        plan.enterStage(PlanStage.SECOND, "SECOND_BASE_RESULT");

        Instant completedAt = Instant.now(clock);
        SecondBaseResultSnapshot snapshot = new SecondBaseResultSnapshot(decision.getRevision(), selected, completedAt);
        submissions.save(new SecondBaseSubmission(planId, decision.getRevision(), snapshotMap(snapshot), completedAt));
        return response(false, snapshot, planService.getProgress(memberId, planId));
    }

    @Transactional(readOnly = true)
    public SecondBaseResultResponse result(Long memberId, Long planId) {
        PlanProgressResponse progress = planService.getProgress(memberId, planId);
        SecondBaseSubmission submission = submissions
                .findFirstByPlanIdOrderByCreatedAtDescIdDesc(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECOND_BASE_RESULT_NOT_FOUND));
        SecondBaseResultSnapshot snapshot = snapshot(submission);
        return new SecondBaseResultResponse(
                snapshot.decisionRevision(), snapshot.decision(), progress, snapshot.completedAt());
    }

    /**
     * 이미 저장한 회차를 다시 받았을 때 돌려줄 진행도.
     *
     * <p>되감기(POST /plans/{planId}/reset)는 관문을 되돌리면서 매물 확정(property_decision)과
     * 제출 기록은 지우지 않는다. 그래서 되감기 뒤 같은 매물·같은 상담으로 다시 확정하면 회차가
     * 그대로라 이 재생 분기로 들어오는데, 여기서 관문을 그냥 두면 2루 관문이 닫힌 채 남아
     * 계획이 3루로 넘어가지 못한다. 몇 번을 다시 내도 응답은 `replayed=true` 뿐이다.
     *
     * <p>관문이 이미 DONE 이면 아무것도 하지 않는다 — 재시도를 안전하게 만드는 재생 분기의
     * 성질을 그대로 두고, 이미 3루로 넘어간 사람의 이어하기 위치도 건드리지 않는다.
     */
    private PlanProgressResponse replayProgress(Long memberId, Long planId, Plan plan, String ruleVersion) {
        PlanProgressResponse progress = planService.getProgress(memberId, planId);
        if (progress.isGateCompleted(PlanGate.SECOND_POLICY_SELECTION)) {
            return progress;
        }
        planService.completeStep(
                memberId, planId, PlanGate.SECOND_POLICY_SELECTION.code(), new CompletePlanStepRequest(ruleVersion));
        plan.enterStage(PlanStage.SECOND, "SECOND_BASE_RESULT");
        return planService.getProgress(memberId, planId);
    }

    private void requireFinalTerms(BankConsultationResponse consultation) {
        boolean incomplete = consultation.resultStatus() != ConsultationResultStatus.POSSIBLE
                || consultation.loanProduct() == ConsultedLoanProduct.UNKNOWN
                || consultation.collateralMethod() == CollateralMethod.UNKNOWN
                || consultation.approvedLimit() == null
                || consultation.quotedRate() == null;
        if (incomplete) {
            throw new BusinessException(ErrorCode.SECOND_BASE_FINAL_TERMS_INCOMPLETE);
        }
    }

    private SecondBaseCompleteResponse response(
            boolean replayed, SecondBaseResultSnapshot snapshot, PlanProgressResponse progress) {
        return new SecondBaseCompleteResponse(
                replayed, snapshot.decisionRevision(), snapshot.decision(), progress, snapshot.completedAt());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshotMap(SecondBaseResultSnapshot snapshot) {
        return objectMapper.convertValue(snapshot, Map.class);
    }

    private SecondBaseResultSnapshot snapshot(SecondBaseSubmission submission) {
        return objectMapper.convertValue(submission.getResultSnapshot(), SecondBaseResultSnapshot.class);
    }
}
