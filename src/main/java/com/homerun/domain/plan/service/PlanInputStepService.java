package com.homerun.domain.plan.service;

import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.dto.request.PlanInputStepSaveRequest;
import com.homerun.domain.plan.dto.response.PlanInputResponse;
import com.homerun.domain.plan.dto.response.PlanInputResumeResponse;
import com.homerun.domain.plan.dto.response.PlanInputStepSaveResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInputStep;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanInputStepRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.DiagnosisInputStep;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.PlanGate;
import com.homerun.domain.plan.type.PlanInputStepStatus;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import com.homerun.domain.plan.validation.PlanInputCompletionValidator;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanInputStepService {

    private static final Set<PlanInputUnknownField> STEP_FIELDS = EnumSet.of(
            PlanInputUnknownField.HOUSEHOLDER_STATUS,
            PlanInputUnknownField.IS_HOMELESS,
            PlanInputUnknownField.HOUSEHOLD_HOMELESS,
            PlanInputUnknownField.MARITAL_STATUS,
            PlanInputUnknownField.EMPLOYMENT_TYPE,
            PlanInputUnknownField.COMPANY_SIZE,
            PlanInputUnknownField.EMPLOYMENT_MONTHS,
            PlanInputUnknownField.MONTHLY_INCOME,
            PlanInputUnknownField.NET_ASSETS,
            PlanInputUnknownField.AVAILABLE_CASH,
            PlanInputUnknownField.EXISTING_JEONSE_LOAN,
            PlanInputUnknownField.INCOME_SOURCE,
            PlanInputUnknownField.ASSET_SOURCE,
            PlanInputUnknownField.FINANCIAL_DATA_CONFIRMED,
            PlanInputUnknownField.HOPE_DEPOSIT,
            PlanInputUnknownField.REGION_ID);

    private final PlanRepository plans;
    private final PlanInputRepository inputs;
    private final PlanInputStepRepository checkpoints;
    private final PlanInputService inputService;
    private final PlanInputCompletionValidator completionValidator;

    public PlanInputStepService(
            PlanRepository plans,
            PlanInputRepository inputs,
            PlanInputStepRepository checkpoints,
            PlanInputService inputService,
            PlanInputCompletionValidator completionValidator) {
        this.plans = plans;
        this.inputs = inputs;
        this.checkpoints = checkpoints;
        this.inputService = inputService;
        this.completionValidator = completionValidator;
    }

    @Transactional
    public PlanInputStepSaveResponse save(
            Long memberId, Long planId, DiagnosisInputStep step, PlanInputStepSaveRequest request) {
        Plan plan = ownedPlan(memberId, planId);
        PlanInputResponse current =
                inputs.findByPlanId(planId).map(PlanInputResponse::from).orElse(null);
        int currentRevision = current == null ? 0 : current.revision();
        if (request.expectedRevision() != currentRevision) {
            throw new BusinessException(ErrorCode.PLAN_INPUT_REVISION_MISMATCH);
        }
        validatePayload(step, request);

        EmploymentType mergedEmployment = value(
                current == null ? null : current.employmentType(),
                step,
                request,
                PlanInputUnknownField.EMPLOYMENT_TYPE);
        boolean employmentChanged = employmentChanged(current, step, request, mergedEmployment);
        PlanInputRequest merged = merge(current, step, request, employmentChanged);
        validateStepCompleted(step, merged);
        PlanInputResponse saved = inputService.save(memberId, planId, merged);
        if (step == DiagnosisInputStep.REVIEW) {
            completionValidator.validate(planId, PlanGate.FIRST_DIAGNOSIS);
        } else if (saved.revision() != currentRevision) {
            checkpoints
                    .findByPlanIdAndStepCode(planId, DiagnosisInputStep.REVIEW)
                    .ifPresent(checkpoints::delete);
        }

        PlanInputStep savedCheckpoint = complete(planId, step);
        synchronizeEmploymentBranch(planId, step, employmentChanged, saved.employmentType());
        DiagnosisInputStep next = step.next(saved.employmentType());
        plan.updateLastLocation(next == null ? "DIAGNOSIS_RESULT" : next.name());

        Progress progress = progress(planId, saved);
        return new PlanInputStepSaveResponse(
                step,
                next,
                progress.completed(),
                progress.skipped(),
                progress.percent(),
                saved.revision(),
                savedCheckpoint.getUpdatedAt(),
                saved);
    }

    @Transactional(readOnly = true)
    public PlanInputResumeResponse resume(Long memberId, Long planId) {
        Plan plan = ownedPlan(memberId, planId);
        PlanInputResponse input =
                inputs.findByPlanId(planId).map(PlanInputResponse::from).orElse(null);
        Progress progress = progress(planId, input);
        DiagnosisInputStep resumeStep = DiagnosisInputStep.find(plan.getLastLocationCode())
                .filter(step -> !progress.completed().contains(step)
                        && !progress.skipped().contains(step))
                .orElseGet(() -> firstIncomplete(input, progress));
        return new PlanInputResumeResponse(
                resumeStep,
                progress.completed(),
                progress.skipped(),
                progress.percent(),
                input == null ? 0 : input.revision(),
                input);
    }

    private Plan ownedPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return plan;
    }

    private void validatePayload(DiagnosisInputStep step, PlanInputStepSaveRequest request) {
        if (!step.fields().containsAll(request.normalizedUnknownFields())) {
            throw new BusinessException(ErrorCode.PLAN_INPUT_STEP_INVALID);
        }
        boolean hasOtherStepValue = STEP_FIELDS.stream()
                .filter(field -> !step.fields().contains(field))
                .anyMatch(field -> request.valueOf(field) != null);
        if (hasOtherStepValue) {
            throw new BusinessException(ErrorCode.PLAN_INPUT_STEP_INVALID);
        }
        boolean unknownHasValue =
                request.normalizedUnknownFields().stream().anyMatch(field -> request.valueOf(field) != null);
        if (unknownHasValue) {
            throw new BusinessException(ErrorCode.UNKNOWN_FIELD_HAS_VALUE);
        }
    }

    private void validateStepCompleted(DiagnosisInputStep step, PlanInputRequest input) {
        boolean complete =
                switch (step) {
                    case HOUSEHOLDER -> answered(input, PlanInputUnknownField.HOUSEHOLDER_STATUS);
                    case HOMELESS -> answered(input, PlanInputUnknownField.IS_HOMELESS);
                    case MARITAL_STATUS -> answered(input, PlanInputUnknownField.MARITAL_STATUS);
                    case EMPLOYMENT_TYPE -> answered(input, PlanInputUnknownField.EMPLOYMENT_TYPE);
                    case COMPANY_SIZE -> answered(input, PlanInputUnknownField.COMPANY_SIZE);
                    case EMPLOYMENT_PERIOD -> answered(input, PlanInputUnknownField.EMPLOYMENT_MONTHS);
                    case FINANCIAL ->
                        answered(input, PlanInputUnknownField.MONTHLY_INCOME)
                                && answered(input, PlanInputUnknownField.NET_ASSETS)
                                && answered(input, PlanInputUnknownField.AVAILABLE_CASH);
                    case HOPE_DEPOSIT -> answered(input, PlanInputUnknownField.HOPE_DEPOSIT);
                    case REGION -> answered(input, PlanInputUnknownField.REGION_ID);
                    case REVIEW -> true;
                };
        if (!complete) {
            throw new BusinessException(ErrorCode.PLAN_INPUT_STEP_INCOMPLETE);
        }
    }

    private boolean answered(PlanInputRequest input, PlanInputUnknownField field) {
        if (input.normalizedUnknownFields().contains(field)) return true;
        return switch (field) {
            case HOUSEHOLDER_STATUS -> input.householderStatus() != null;
            case IS_HOMELESS -> input.isHomeless() != null;
            case MARITAL_STATUS -> input.maritalStatus() != null;
            case EMPLOYMENT_TYPE -> input.employmentType() != null;
            case COMPANY_SIZE -> input.companySize() != null;
            case EMPLOYMENT_MONTHS -> input.employmentMonths() != null;
            case MONTHLY_INCOME -> input.monthlyIncome() != null;
            case NET_ASSETS -> input.netAssets() != null;
            case HOPE_DEPOSIT -> input.hopeDeposit() != null;
            case REGION_ID -> input.regionId() != null;
            default -> false;
        };
    }

    private PlanInputRequest merge(
            PlanInputResponse current,
            DiagnosisInputStep step,
            PlanInputStepSaveRequest request,
            boolean employmentChanged) {
        EmploymentType mergedEmployment = value(
                current == null ? null : current.employmentType(),
                step,
                request,
                PlanInputUnknownField.EMPLOYMENT_TYPE);
        Set<PlanInputUnknownField> unknown = mergedUnknownFields(current, step, request, employmentChanged);
        return new PlanInputRequest(
                value(
                        current == null ? null : current.hopeDeposit(),
                        step,
                        request,
                        PlanInputUnknownField.HOPE_DEPOSIT),
                current == null ? null : current.currentDeposit(),
                current == null ? null : current.monthlyRent(),
                current == null ? null : current.maintenanceFee(),
                current == null ? null : current.maxMonthlyBurden(),
                value(current == null ? null : current.regionId(), step, request, PlanInputUnknownField.REGION_ID),
                current == null ? null : current.areaM2(),
                current == null ? null : current.houseType(),
                value(current == null ? null : current.isHomeless(), step, request, PlanInputUnknownField.IS_HOMELESS),
                value(
                        current == null ? null : current.householderStatus(),
                        step,
                        request,
                        PlanInputUnknownField.HOUSEHOLDER_STATUS),
                value(
                        current == null ? null : current.maritalStatus(),
                        step,
                        request,
                        PlanInputUnknownField.MARITAL_STATUS),
                mergedEmployment,
                employmentChanged
                        ? null
                        : value(
                                current == null ? null : current.employmentMonths(),
                                step,
                                request,
                                PlanInputUnknownField.EMPLOYMENT_MONTHS),
                employmentChanged
                        ? null
                        : value(
                                current == null ? null : current.companySize(),
                                step,
                                request,
                                PlanInputUnknownField.COMPANY_SIZE),
                value(
                        current == null ? null : current.householdHomeless(),
                        step,
                        request,
                        PlanInputUnknownField.HOUSEHOLD_HOMELESS),
                current == null ? null : current.birthDate(),
                current == null ? null : current.militaryMonths(),
                value(
                        current == null ? null : current.monthlyIncome(),
                        step,
                        request,
                        PlanInputUnknownField.MONTHLY_INCOME),
                value(current == null ? null : current.netAssets(), step, request, PlanInputUnknownField.NET_ASSETS),
                value(
                        current == null ? null : current.availableCash(),
                        step,
                        request,
                        PlanInputUnknownField.AVAILABLE_CASH),
                value(
                        current == null ? null : current.existingJeonseLoan(),
                        step,
                        request,
                        PlanInputUnknownField.EXISTING_JEONSE_LOAN),
                value(
                        current == null ? null : current.incomeSource(),
                        step,
                        request,
                        PlanInputUnknownField.INCOME_SOURCE),
                value(
                        current == null ? null : current.assetSource(),
                        step,
                        request,
                        PlanInputUnknownField.ASSET_SOURCE),
                value(
                        current == null ? null : current.financialDataConfirmed(),
                        step,
                        request,
                        PlanInputUnknownField.FINANCIAL_DATA_CONFIRMED),
                current == null ? null : current.livesApartFromParents(),
                current == null ? null : current.parentOnHousingBenefit(),
                unknown);
    }

    private boolean employmentChanged(
            PlanInputResponse current,
            DiagnosisInputStep step,
            PlanInputStepSaveRequest request,
            EmploymentType mergedEmployment) {
        if (step != DiagnosisInputStep.EMPLOYMENT_TYPE) return false;
        boolean previousUnknown =
                current != null && current.unknownFields().contains(PlanInputUnknownField.EMPLOYMENT_TYPE);
        boolean requestedUnknown = request.normalizedUnknownFields().contains(PlanInputUnknownField.EMPLOYMENT_TYPE);
        if (request.employmentType() != null) {
            return previousUnknown
                    || !Objects.equals(current == null ? null : current.employmentType(), mergedEmployment);
        }
        return requestedUnknown && (!previousUnknown || (current != null && current.employmentType() != null));
    }

    @SuppressWarnings("unchecked")
    private <T> T value(
            T current, DiagnosisInputStep step, PlanInputStepSaveRequest request, PlanInputUnknownField field) {
        if (!step.fields().contains(field)) return current;
        if (request.normalizedUnknownFields().contains(field)) return null;
        Object requested = request.valueOf(field);
        return requested == null ? current : (T) requested;
    }

    private Set<PlanInputUnknownField> mergedUnknownFields(
            PlanInputResponse current,
            DiagnosisInputStep step,
            PlanInputStepSaveRequest request,
            boolean employmentChanged) {
        Set<PlanInputUnknownField> result = EnumSet.noneOf(PlanInputUnknownField.class);
        if (current != null && current.unknownFields() != null) {
            result.addAll(current.unknownFields());
        }
        for (PlanInputUnknownField field : step.fields()) {
            if (request.valueOf(field) != null) result.remove(field);
            if (request.normalizedUnknownFields().contains(field)) result.add(field);
        }
        if (employmentChanged) {
            result.remove(PlanInputUnknownField.COMPANY_SIZE);
            result.remove(PlanInputUnknownField.EMPLOYMENT_MONTHS);
        }
        return Set.copyOf(result);
    }

    private PlanInputStep complete(Long planId, DiagnosisInputStep step) {
        PlanInputStep checkpoint =
                checkpoints.findByPlanIdAndStepCode(planId, step).orElseGet(() -> new PlanInputStep(planId, step));
        checkpoint.complete();
        return checkpoints.save(checkpoint);
    }

    private void synchronizeEmploymentBranch(
            Long planId, DiagnosisInputStep savedStep, boolean employmentChanged, EmploymentType employmentType) {
        if (savedStep != DiagnosisInputStep.EMPLOYMENT_TYPE || !employmentChanged) return;
        for (DiagnosisInputStep branchStep :
                List.of(DiagnosisInputStep.COMPANY_SIZE, DiagnosisInputStep.EMPLOYMENT_PERIOD)) {
            if (DiagnosisInputStep.isSalaried(employmentType)) {
                checkpoints.findByPlanIdAndStepCode(planId, branchStep).ifPresent(checkpoints::delete);
            } else {
                PlanInputStep checkpoint = checkpoints
                        .findByPlanIdAndStepCode(planId, branchStep)
                        .orElseGet(() -> new PlanInputStep(planId, branchStep));
                checkpoint.skip();
                checkpoints.save(checkpoint);
            }
        }
    }

    private Progress progress(Long planId, PlanInputResponse input) {
        Map<DiagnosisInputStep, PlanInputStepStatus> statuses = new EnumMap<>(DiagnosisInputStep.class);
        checkpoints.findAllByPlanId(planId).forEach(item -> statuses.put(item.getStepCode(), item.getStatus()));
        EmploymentType employmentType = input == null ? null : input.employmentType();
        boolean employmentUnknown =
                input != null && input.unknownFields().contains(PlanInputUnknownField.EMPLOYMENT_TYPE);
        List<DiagnosisInputStep> path = employmentUnknown
                ? DiagnosisInputStep.path(EmploymentType.FREELANCER)
                : DiagnosisInputStep.path(employmentType);
        List<DiagnosisInputStep> completed = new ArrayList<>();
        List<DiagnosisInputStep> skipped = new ArrayList<>();
        for (DiagnosisInputStep step : DiagnosisInputStep.values()) {
            PlanInputStepStatus status = statuses.get(step);
            if (status == PlanInputStepStatus.SKIPPED || !path.contains(step)) {
                skipped.add(step);
            } else if (status == PlanInputStepStatus.COMPLETED || inferredCompleted(step, input)) {
                completed.add(step);
            }
        }
        int completedOnPath = (int) completed.stream().filter(path::contains).count();
        int percent = path.isEmpty() ? 0 : completedOnPath * 100 / path.size();
        return new Progress(completed, skipped, path, percent);
    }

    private DiagnosisInputStep firstIncomplete(PlanInputResponse input, Progress progress) {
        return progress.path().stream()
                .filter(step -> !progress.completed().contains(step))
                .findFirst()
                .orElse(null);
    }

    private boolean inferredCompleted(DiagnosisInputStep step, PlanInputResponse input) {
        if (input == null || step == DiagnosisInputStep.REVIEW) return false;
        return switch (step) {
            case HOUSEHOLDER -> answered(input, PlanInputUnknownField.HOUSEHOLDER_STATUS);
            case HOMELESS -> answered(input, PlanInputUnknownField.IS_HOMELESS);
            case MARITAL_STATUS -> answered(input, PlanInputUnknownField.MARITAL_STATUS);
            case EMPLOYMENT_TYPE -> answered(input, PlanInputUnknownField.EMPLOYMENT_TYPE);
            case COMPANY_SIZE -> answered(input, PlanInputUnknownField.COMPANY_SIZE);
            case EMPLOYMENT_PERIOD -> answered(input, PlanInputUnknownField.EMPLOYMENT_MONTHS);
            case FINANCIAL ->
                answered(input, PlanInputUnknownField.MONTHLY_INCOME)
                        && answered(input, PlanInputUnknownField.NET_ASSETS)
                        && answered(input, PlanInputUnknownField.AVAILABLE_CASH);
            case HOPE_DEPOSIT -> answered(input, PlanInputUnknownField.HOPE_DEPOSIT);
            case REGION -> answered(input, PlanInputUnknownField.REGION_ID);
            case REVIEW -> false;
        };
    }

    private boolean answered(PlanInputResponse input, PlanInputUnknownField field) {
        if (input == null) return false;
        if (input.unknownFields().contains(field)) return true;
        return switch (field) {
            case HOUSEHOLDER_STATUS -> input.householderStatus() != null;
            case IS_HOMELESS -> input.isHomeless() != null;
            case HOUSEHOLD_HOMELESS -> input.householdHomeless() != null;
            case MARITAL_STATUS -> input.maritalStatus() != null;
            case EMPLOYMENT_TYPE -> input.employmentType() != null;
            case COMPANY_SIZE -> input.companySize() != null;
            case EMPLOYMENT_MONTHS -> input.employmentMonths() != null;
            case MONTHLY_INCOME -> input.monthlyIncome() != null;
            case NET_ASSETS -> input.netAssets() != null;
            case AVAILABLE_CASH -> input.availableCash() != null;
            case EXISTING_JEONSE_LOAN -> input.existingJeonseLoan() != null;
            case INCOME_SOURCE -> input.incomeSource() != null;
            case ASSET_SOURCE -> input.assetSource() != null;
            case FINANCIAL_DATA_CONFIRMED -> input.financialDataConfirmed() != null;
            case HOPE_DEPOSIT -> input.hopeDeposit() != null;
            case REGION_ID -> input.regionId() != null;
            default -> false;
        };
    }

    private record Progress(
            List<DiagnosisInputStep> completed,
            List<DiagnosisInputStep> skipped,
            List<DiagnosisInputStep> path,
            int percent) {}
}
