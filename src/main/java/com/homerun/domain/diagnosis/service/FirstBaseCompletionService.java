package com.homerun.domain.diagnosis.service;

import com.homerun.domain.diagnosis.dto.request.FirstBaseCompleteRequest;
import com.homerun.domain.diagnosis.dto.response.DiagnosisResponse;
import com.homerun.domain.diagnosis.dto.response.FirstBaseCompleteResponse;
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
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.exception.FieldValidationException;
import com.homerun.global.response.FieldErrorDetail;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PlanService planService;

    public FirstBaseCompletionService(
            PlanRepository plans,
            PlanInputRepository inputs,
            PlanInputStepRepository inputSteps,
            FirstBaseSubmissionRepository submissions,
            PlanInputCompletionValidator inputValidator,
            DiagnosisService diagnosisService,
            PlanService planService) {
        this.plans = plans;
        this.inputs = inputs;
        this.inputSteps = inputSteps;
        this.submissions = submissions;
        this.inputValidator = inputValidator;
        this.diagnosisService = diagnosisService;
        this.planService = planService;
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
            return completedResponse(
                    input.getRevision(),
                    true,
                    diagnosisService.get(memberId, planId, previous.getDiagnosisId()),
                    planService.getProgress(memberId, planId));
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
                    confirmationFields,
                    planService.getProgress(memberId, planId));
        }

        DiagnosisResponse diagnosis = diagnosisService.calculate(memberId, planId, request.calculation());
        PlanProgressResponse progress = planService.completeStep(
                memberId, planId, PlanGate.FIRST_DIAGNOSIS.code(), new CompletePlanStepRequest(request.ruleVersion()));
        plan.enterStage(PlanStage.FIRST, "DIAGNOSIS_RESULT");
        submissions.save(new FirstBaseSubmission(planId, input.getRevision(), diagnosis.diagnosisId()));

        return completedResponse(input.getRevision(), false, diagnosis, progressAfterLocation(plan, progress));
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
            int inputRevision, boolean replayed, DiagnosisResponse diagnosis, PlanProgressResponse progress) {
        return new FirstBaseCompleteResponse(
                FirstBaseCompletionStatus.COMPLETED, replayed, inputRevision, diagnosis, List.of(), progress);
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
