package com.homerun.domain.plan.service;

import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.dto.response.PlanInputResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.entity.PlanInputHistory;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.repository.PlanInputHistoryRepository;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.type.PlanGate;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import com.homerun.domain.region.repository.RegionRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanInputService {

    private final PlanRepository planRepository;
    private final PlanInputRepository inputRepository;
    private final PlanInputHistoryRepository historyRepository;
    private final PlanStepRepository stepRepository;
    private final RegionRepository regionRepository;

    public PlanInputService(
            PlanRepository planRepository,
            PlanInputRepository inputRepository,
            PlanInputHistoryRepository historyRepository,
            PlanStepRepository stepRepository,
            RegionRepository regionRepository) {
        this.planRepository = planRepository;
        this.inputRepository = inputRepository;
        this.historyRepository = historyRepository;
        this.stepRepository = stepRepository;
        this.regionRepository = regionRepository;
    }

    @Transactional
    public PlanInputResponse save(Long memberId, Long planId, PlanInputRequest request) {
        validateUnknownFields(request);
        findOwnedPlan(memberId, planId);
        validateRegion(request.regionId());
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

    private void validateRegion(Long regionId) {
        if (regionId != null && !regionRepository.existsById(regionId)) {
            throw new BusinessException(ErrorCode.PLAN_REGION_NOT_FOUND);
        }
    }

    // 모름으로 둘 수 있는 항목은 PlanInputUnknownField 가 정한다. 역직렬화 단계에서
    // 걸러지므로 여기서는 값이 함께 오지 않았는지만 본다(COM-05-04).
    private void validateUnknownFields(PlanInputRequest request) {
        for (PlanInputUnknownField field : request.normalizedUnknownFields()) {
            if (valueOf(request, field) != null) {
                throw new BusinessException(ErrorCode.UNKNOWN_FIELD_HAS_VALUE);
            }
        }
    }

    private Object valueOf(PlanInputRequest request, PlanInputUnknownField field) {
        return switch (field) {
            case HOPE_DEPOSIT -> request.hopeDeposit();
            case CURRENT_DEPOSIT -> request.currentDeposit();
            case MONTHLY_RENT -> request.monthlyRent();
            case MAINTENANCE_FEE -> request.maintenanceFee();
            case MAX_MONTHLY_BURDEN -> request.maxMonthlyBurden();
            case REGION_ID -> request.regionId();
            case AREA_M2 -> request.areaM2();
            case HOUSE_TYPE -> request.houseType();
            case IS_HOMELESS -> request.isHomeless();
            case HOUSEHOLDER_STATUS -> request.householderStatus();
            case MARITAL_STATUS -> request.maritalStatus();
            case EMPLOYMENT_TYPE -> request.employmentType();
            case EMPLOYMENT_MONTHS -> request.employmentMonths();
            case COMPANY_SIZE -> request.companySize();
        };
    }
}
