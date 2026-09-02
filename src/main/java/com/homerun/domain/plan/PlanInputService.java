package com.homerun.domain.plan;

import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanInputService {

    private static final Set<String> UNKNOWN_CAPABLE_FIELDS = Set.of(
            "hopeDeposit",
            "currentDeposit",
            "monthlyRent",
            "maintenanceFee",
            "maxMonthlyBurden",
            "regionId",
            "areaM2",
            "houseType",
            "isHomeless",
            "householderStatus",
            "maritalStatus",
            "employmentType",
            "employmentMonths",
            "companySize");

    private final PlanRepository planRepository;
    private final PlanInputRepository inputRepository;
    private final PlanInputHistoryRepository historyRepository;
    private final PlanStepRepository stepRepository;

    public PlanInputService(
            PlanRepository planRepository,
            PlanInputRepository inputRepository,
            PlanInputHistoryRepository historyRepository,
            PlanStepRepository stepRepository) {
        this.planRepository = planRepository;
        this.inputRepository = inputRepository;
        this.historyRepository = historyRepository;
        this.stepRepository = stepRepository;
    }

    @Transactional
    public PlanInputResponse save(Long memberId, Long planId, PlanInputRequest request) {
        validateUnknownFields(request);
        findOwnedPlan(memberId, planId);
        PlanInput input = inputRepository.findByPlanId(planId).orElse(null);
        if (input == null) {
            return PlanInputResponse.from(inputRepository.save(PlanInput.create(planId, request)));
        }
        if (input.matches(request)) {
            return PlanInputResponse.from(input);
        }

        historyRepository.save(PlanInputHistory.capture(input));
        input.update(request);
        markAffectedStepsForRecalculation(planId);
        return PlanInputResponse.from(input);
    }

    @Transactional(readOnly = true)
    public PlanInputResponse get(Long memberId, Long planId) {
        findOwnedPlan(memberId, planId);
        PlanInput input = inputRepository
                .findByPlanId(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_INPUT_NOT_FOUND));
        return PlanInputResponse.from(input);
    }

    private Plan findOwnedPlan(Long memberId, Long planId) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return plan;
    }

    private void markAffectedStepsForRecalculation(Long planId) {
        List<PlanStep> steps = stepRepository.findAllByPlanIdOrderBySequenceAsc(planId);
        steps.stream()
                .filter(step -> step.getSequence() >= PlanGate.FIRST_DIAGNOSIS.sequence())
                .forEach(PlanStep::requireRecalculation);
    }

    private void validateUnknownFields(PlanInputRequest request) {
        for (String field : request.normalizedUnknownFields()) {
            if (!UNKNOWN_CAPABLE_FIELDS.contains(field)) {
                throw new BusinessException(ErrorCode.INVALID_UNKNOWN_FIELD);
            }
            if (valueOf(request, field) != null) {
                throw new BusinessException(ErrorCode.UNKNOWN_FIELD_HAS_VALUE);
            }
        }
    }

    private Object valueOf(PlanInputRequest request, String field) {
        return switch (field) {
            case "hopeDeposit" -> request.hopeDeposit();
            case "currentDeposit" -> request.currentDeposit();
            case "monthlyRent" -> request.monthlyRent();
            case "maintenanceFee" -> request.maintenanceFee();
            case "maxMonthlyBurden" -> request.maxMonthlyBurden();
            case "regionId" -> request.regionId();
            case "areaM2" -> request.areaM2();
            case "houseType" -> request.houseType();
            case "isHomeless" -> request.isHomeless();
            case "householderStatus" -> request.householderStatus();
            case "maritalStatus" -> request.maritalStatus();
            case "employmentType" -> request.employmentType();
            case "employmentMonths" -> request.employmentMonths();
            case "companySize" -> request.companySize();
            default -> null;
        };
    }
}
