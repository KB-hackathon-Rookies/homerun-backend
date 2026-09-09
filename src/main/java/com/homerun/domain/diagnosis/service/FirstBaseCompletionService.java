package com.homerun.domain.diagnosis.service;

import com.homerun.domain.diagnosis.dto.request.FirstBaseCompleteRequest;
import com.homerun.domain.diagnosis.dto.response.DiagnosisResponse;
import com.homerun.domain.diagnosis.dto.response.FirstBaseCompleteResponse;
import com.homerun.domain.diagnosis.dto.response.FirstBaseLoanScenarioResponse;
import com.homerun.domain.diagnosis.dto.response.FirstBaseResultResponse;
import com.homerun.domain.diagnosis.dto.response.FirstBaseResultSnapshot;
import com.homerun.domain.diagnosis.entity.FirstBaseSubmission;
import com.homerun.domain.diagnosis.repository.FirstBaseSubmissionRepository;
import com.homerun.domain.diagnosis.type.FirstBaseCompletionStatus;
import com.homerun.domain.plan.dto.request.CompletePlanStepRequest;
import com.homerun.domain.plan.dto.response.PlanProgressResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanInputStepRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.service.PlanService;
import com.homerun.domain.plan.type.DiagnosisInputStep;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.PlanGate;
import com.homerun.domain.plan.type.PlanInputStepStatus;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.plan.validation.PlanInputCompletionValidator;
import com.homerun.domain.policy.dto.response.JeonseLoanCardResponse;
import com.homerun.domain.policy.dto.response.JeonsePolicyVerdictListResponse;
import com.homerun.domain.policy.service.JeonsePolicyVerdictService;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.exception.FieldValidationException;
import com.homerun.global.response.FieldErrorDetail;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class FirstBaseCompletionService {

    private static final Set<PlanInputUnknownField> REQUIRED_CONFIRMATION_FIELDS = EnumSet.of(
            PlanInputUnknownField.HOPE_DEPOSIT,
            PlanInputUnknownField.REGION_ID,
            PlanInputUnknownField.IS_HOMELESS,
            PlanInputUnknownField.HOUSEHOLDER_STATUS,
            PlanInputUnknownField.EMPLOYMENT_TYPE,
            PlanInputUnknownField.MONTHLY_INCOME,
            PlanInputUnknownField.NET_ASSETS,
            PlanInputUnknownField.AVAILABLE_CASH);

    private final PlanRepository plans;
    private final PlanInputRepository inputs;
    private final PlanInputStepRepository inputSteps;
    private final FirstBaseSubmissionRepository submissions;
    private final PlanInputCompletionValidator inputValidator;
    private final DiagnosisService diagnosisService;
    private final JeonsePolicyVerdictService policyVerdictService;
    private final PlanService planService;
    private final ObjectMapper objectMapper;

    public FirstBaseCompletionService(
            PlanRepository plans,
            PlanInputRepository inputs,
            PlanInputStepRepository inputSteps,
            FirstBaseSubmissionRepository submissions,
            PlanInputCompletionValidator inputValidator,
            DiagnosisService diagnosisService,
            JeonsePolicyVerdictService policyVerdictService,
            PlanService planService,
            ObjectMapper objectMapper) {
        this.plans = plans;
        this.inputs = inputs;
        this.inputSteps = inputSteps;
        this.submissions = submissions;
        this.inputValidator = inputValidator;
        this.diagnosisService = diagnosisService;
        this.policyVerdictService = policyVerdictService;
        this.planService = planService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FirstBaseCompleteResponse complete(Long memberId, Long planId, FirstBaseCompleteRequest request) {
        Plan plan = plans.findByIdForUpdate(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        plan.verifyRuleVersion(request.ruleVersion());

        PlanInput input =
                inputs.findByPlanId(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_INPUT_NOT_FOUND));
        if (input.getRevision() != request.expectedRevision()) {
            throw new BusinessException(ErrorCode.PLAN_INPUT_REVISION_MISMATCH);
        }

        FirstBaseSubmission previous = submissions
                .findByPlanIdAndInputRevision(planId, input.getRevision())
                .orElse(null);
        if (previous != null) {
            FirstBaseResultSnapshot snapshot = snapshot(memberId, planId, previous);
            return completedResponse(true, snapshot, replayProgress(memberId, planId, plan, request.ruleVersion()));
        }

        requireReviewCompleted(planId);
        inputValidator.validate(planId, PlanGate.FIRST_DIAGNOSIS);
        validateFinalInput(input);
        List<PlanInputUnknownField> confirmationFields = confirmationFields(input);
        if (!confirmationFields.isEmpty()) {
            return new FirstBaseCompleteResponse(
                    FirstBaseCompletionStatus.NEEDS_CONFIRMATION,
                    false,
                    input.getRevision(),
                    null,
                    null,
                    List.of(),
                    confirmationFields,
                    planService.getProgress(memberId, planId),
                    null);
        }

        DiagnosisResponse diagnosis = diagnosisService.calculate(
                memberId, planId, request.calculation().toCalculation(0, 0));
        JeonsePolicyVerdictListResponse policies = policyVerdictService.evaluate(memberId, planId);
        List<FirstBaseLoanScenarioResponse> loanScenarios = policies.cards().stream()
                .filter(card -> card.type() == JeonseLoanCardResponse.CardType.POLICY)
                .filter(card -> card.estimate() != null
                        && card.estimate().estimatedLoanAmount() != null
                        && card.estimate().monthlyInterestMin() != null
                        && card.estimate().monthlyInterestMax() != null)
                .map(card -> scenario(memberId, planId, request, card))
                .toList();
        PlanProgressResponse progress = planService.completeStep(
                memberId, planId, PlanGate.FIRST_DIAGNOSIS.code(), new CompletePlanStepRequest(request.ruleVersion()));
        plan.enterStage(PlanStage.FIRST, "DIAGNOSIS_RESULT");
        FirstBaseResultSnapshot snapshot = new FirstBaseResultSnapshot(
                input.getRevision(), diagnosis, policies, loanScenarios, diagnosis.calculatedAt());
        submissions.save(
                new FirstBaseSubmission(planId, input.getRevision(), diagnosis.diagnosisId(), snapshotMap(snapshot)));

        return completedResponse(false, snapshot, progressAfterLocation(plan, progress));
    }

    @Transactional(readOnly = true)
    public FirstBaseResultResponse result(Long memberId, Long planId) {
        PlanProgressResponse progress = planService.getProgress(memberId, planId);
        FirstBaseSubmission submission = submissions
                .findFirstByPlanIdOrderByCreatedAtDescIdDesc(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FIRST_BASE_RESULT_NOT_FOUND));
        return FirstBaseResultResponse.from(snapshot(memberId, planId, submission), progress);
    }

    /**
     * 이미 저장한 회차를 다시 받았을 때 돌려줄 진행도.
     *
     * <p>되감기(POST /plans/{planId}/reset)는 관문을 되돌리면서 제출 기록은 지우지 않는다.
     * 그래서 사용자가 답을 그대로 두고 다시 제출하면 회차가 같아 이 재생 분기로 들어오는데,
     * 여기서 관문을 그냥 두면 1루 관문이 닫힌 채 남아 계획이 그 자리에서 영영 멈춘다 —
     * 몇 번을 다시 내도 응답은 `replayed=true` 뿐이라 화면에 빠져나갈 길이 없다.
     *
     * <p>재생이 같은 결과를 돌려주는 것만으로는 멱등이 아니다. 여러 번 부른 뒤의 계획 상태가
     * 한 번 부른 뒤와 같아야 멱등이다. 그래서 결과는 그대로 다시 주되, 아직 닫혀 있는 관문만
     * 마저 통과시킨다.
     *
     * <p>관문이 이미 DONE 이면 아무것도 하지 않는다. 재시도를 안전하게 만드는 것이 재생 분기의
     * 존재 이유이고, 이미 2루로 넘어간 사람의 이어하기 위치를 1루로 되돌려서도 안 된다.
     */
    private PlanProgressResponse replayProgress(Long memberId, Long planId, Plan plan, String ruleVersion) {
        PlanProgressResponse progress = planService.getProgress(memberId, planId);
        if (progress.isGateCompleted(PlanGate.FIRST_DIAGNOSIS)) {
            return progress;
        }
        PlanProgressResponse reopened = planService.completeStep(
                memberId, planId, PlanGate.FIRST_DIAGNOSIS.code(), new CompletePlanStepRequest(ruleVersion));
        plan.enterStage(PlanStage.FIRST, "DIAGNOSIS_RESULT");
        return progressAfterLocation(plan, reopened);
    }

    private FirstBaseLoanScenarioResponse scenario(
            Long memberId, Long planId, FirstBaseCompleteRequest request, JeonseLoanCardResponse card) {
        long loan = card.estimate().estimatedLoanAmount();
        DiagnosisResponse minimumRate = diagnosisService.simulate(
                memberId,
                planId,
                request.calculation().toCalculation(loan, card.estimate().monthlyInterestMin()));
        DiagnosisResponse maximumRate = diagnosisService.simulate(
                memberId,
                planId,
                request.calculation().toCalculation(loan, card.estimate().monthlyInterestMax()));
        return new FirstBaseLoanScenarioResponse(card, minimumRate, maximumRate);
    }

    private FirstBaseResultSnapshot snapshot(Long memberId, Long planId, FirstBaseSubmission submission) {
        if (submission.getResultSnapshot() != null) {
            return objectMapper.convertValue(submission.getResultSnapshot(), FirstBaseResultSnapshot.class);
        }
        DiagnosisResponse diagnosis = diagnosisService.get(memberId, planId, submission.getDiagnosisId());
        JeonsePolicyVerdictListResponse emptyPolicies =
                new JeonsePolicyVerdictListResponse(planId, List.of(), submission.getCreatedAt(), List.of());
        return new FirstBaseResultSnapshot(
                submission.getInputRevision(), diagnosis, emptyPolicies, List.of(), submission.getCreatedAt());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshotMap(FirstBaseResultSnapshot snapshot) {
        return objectMapper.convertValue(snapshot, Map.class);
    }

    private void requireReviewCompleted(Long planId) {
        boolean completed = inputSteps.existsByPlanIdAndStepCodeAndStatus(
                planId, DiagnosisInputStep.REVIEW, PlanInputStepStatus.COMPLETED);
        if (!completed) {
            throw new BusinessException(ErrorCode.FIRST_BASE_REVIEW_REQUIRED);
        }
    }

    private List<PlanInputUnknownField> confirmationFields(PlanInput input) {
        Set<PlanInputUnknownField> required = requiredFields(input);
        List<PlanInputUnknownField> result = new ArrayList<>(input.getUnknownFields());
        result.retainAll(required);
        result.sort(Comparator.comparing(Enum::name));
        return List.copyOf(result);
    }

    private void validateFinalInput(PlanInput input) {
        Set<PlanInputUnknownField> unknown = Set.copyOf(input.getUnknownFields());
        List<FieldErrorDetail> missing = requiredFields(input).stream()
                .filter(field -> !unknown.contains(field) && value(input, field) == null)
                .sorted(Comparator.comparing(Enum::name))
                .map(field -> new FieldErrorDetail(fieldName(field), "값을 입력하거나 모름으로 표시해 주세요."))
                .toList();
        if (!missing.isEmpty()) {
            throw new FieldValidationException(ErrorCode.PLAN_REQUIRED_INPUT_MISSING, missing);
        }
    }

    private Set<PlanInputUnknownField> requiredFields(PlanInput input) {
        Set<PlanInputUnknownField> required = EnumSet.copyOf(REQUIRED_CONFIRMATION_FIELDS);
        if (isSalaried(input.getEmploymentType())) {
            required.add(PlanInputUnknownField.COMPANY_SIZE);
            required.add(PlanInputUnknownField.EMPLOYMENT_MONTHS);
        }
        return required;
    }

    private Object value(PlanInput input, PlanInputUnknownField field) {
        return switch (field) {
            case HOPE_DEPOSIT -> input.getHopeDeposit();
            case REGION_ID -> input.getRegionId();
            case IS_HOMELESS -> input.getHomeless();
            case HOUSEHOLDER_STATUS -> input.getHouseholderStatus();
            case EMPLOYMENT_TYPE -> input.getEmploymentType();
            case EMPLOYMENT_MONTHS -> input.getEmploymentMonths();
            case COMPANY_SIZE -> input.getCompanySize();
            case MONTHLY_INCOME -> input.getMonthlyIncome();
            case NET_ASSETS -> input.getNetAssets();
            case AVAILABLE_CASH -> input.getAvailableCash();
            default -> null;
        };
    }

    private String fieldName(PlanInputUnknownField field) {
        return switch (field) {
            case HOPE_DEPOSIT -> "hopeDeposit";
            case REGION_ID -> "regionId";
            case IS_HOMELESS -> "isHomeless";
            case HOUSEHOLDER_STATUS -> "householderStatus";
            case EMPLOYMENT_TYPE -> "employmentType";
            case EMPLOYMENT_MONTHS -> "employmentMonths";
            case COMPANY_SIZE -> "companySize";
            case MONTHLY_INCOME -> "monthlyIncome";
            case NET_ASSETS -> "netAssets";
            case AVAILABLE_CASH -> "availableCash";
            default -> field.name();
        };
    }

    private boolean isSalaried(EmploymentType employmentType) {
        return DiagnosisInputStep.isSalaried(employmentType);
    }

    private FirstBaseCompleteResponse completedResponse(
            boolean replayed, FirstBaseResultSnapshot snapshot, PlanProgressResponse progress) {
        return new FirstBaseCompleteResponse(
                FirstBaseCompletionStatus.COMPLETED,
                replayed,
                snapshot.inputRevision(),
                snapshot.diagnosis(),
                snapshot.policies(),
                snapshot.loanScenarios(),
                List.of(),
                progress,
                snapshot.completedAt());
    }

    private PlanProgressResponse progressAfterLocation(Plan plan, PlanProgressResponse progress) {
        return new PlanProgressResponse(
                progress.planId(),
                progress.currentStage(),
                plan.getLastVisitedStage(),
                progress.planStatus(),
                plan.getLastLocationCode(),
                progress.completedSteps(),
                progress.totalSteps(),
                progress.progressPercent(),
                progress.steps());
    }
}
