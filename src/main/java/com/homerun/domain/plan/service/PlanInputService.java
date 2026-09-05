package com.homerun.domain.plan.service;

import com.homerun.domain.plan.dto.request.FinancialIncomeConfirmationRequest;
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
import com.homerun.domain.plan.type.FinancialIncomeAction;
import com.homerun.domain.plan.type.FinancialValueSource;
import com.homerun.domain.plan.type.OpenBankingIncomeSyncStatus;
import com.homerun.domain.plan.type.PlanGate;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import com.homerun.domain.region.repository.RegionRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import java.util.Objects;
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
        validateFinancialSources(request, input);
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

    @Transactional(readOnly = true)
    public void verifyOwner(Long memberId, Long planId) {
        findOwnedPlan(memberId, planId);
    }

    @Transactional
    public OpenBankingIncomeSyncResult syncOpenBankingIncome(Long memberId, Long planId, Long income) {
        findOwnedPlan(memberId, planId);
        PlanInput input = inputRepository.findByPlanId(planId).orElse(null);
        if (income == null)
            return new OpenBankingIncomeSyncResult(
                    OpenBankingIncomeSyncStatus.NOT_APPLICABLE, input == null ? null : PlanInputResponse.from(input));
        if (input == null) {
            input = inputRepository.save(PlanInput.createWithOpenBankingIncome(planId, income));
            markAffectedStepsForRecalculation(planId);
            return new OpenBankingIncomeSyncResult(OpenBankingIncomeSyncStatus.APPLIED, PlanInputResponse.from(input));
        }
        if (input.getIncomeSource() == FinancialValueSource.MANUAL) {
            return new OpenBankingIncomeSyncResult(
                    OpenBankingIncomeSyncStatus.MANUAL_VALUE_PRESERVED, PlanInputResponse.from(input));
        }
        if (Boolean.TRUE.equals(input.getFinancialDataConfirmed())) {
            return new OpenBankingIncomeSyncResult(
                    OpenBankingIncomeSyncStatus.CONFIRMED_VALUE_PRESERVED, PlanInputResponse.from(input));
        }
        PlanInputHistory previous = PlanInputHistory.capture(input);
        if (!input.syncOpenBankingIncome(income)) {
            return new OpenBankingIncomeSyncResult(
                    OpenBankingIncomeSyncStatus.UNCHANGED, PlanInputResponse.from(input));
        }
        historyRepository.save(previous);
        markAffectedStepsForRecalculation(planId);
        return new OpenBankingIncomeSyncResult(OpenBankingIncomeSyncStatus.APPLIED, PlanInputResponse.from(input));
    }

    @Transactional
    public PlanInputResponse confirmFinancialIncome(
            Long memberId, Long planId, FinancialIncomeConfirmationRequest request) {
        findOwnedPlan(memberId, planId);
        PlanInput input = inputRepository
                .findByPlanId(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FINANCIAL_INCOME_CONFIRMATION));
        if (request.action() == null) {
            throw new BusinessException(ErrorCode.INVALID_FINANCIAL_INCOME_CONFIRMATION);
        }
        boolean changed;
        PlanInputHistory previous = PlanInputHistory.capture(input);
        if (request.action() == FinancialIncomeAction.CONFIRM_OPEN_BANKING) {
            if (request.monthlyIncome() != null
                    || input.getMonthlyIncome() == null
                    || input.getIncomeSource() != FinancialValueSource.OPEN_BANKING
                    || (input.getAssetSource() == FinancialValueSource.OPEN_BANKING
                            && !Boolean.TRUE.equals(input.getFinancialDataConfirmed()))) {
                throw new BusinessException(ErrorCode.INVALID_FINANCIAL_INCOME_CONFIRMATION);
            }
            changed = input.confirmOpenBankingIncome();
        } else {
            if (request.monthlyIncome() == null) {
                throw new BusinessException(ErrorCode.INVALID_FINANCIAL_INCOME_CONFIRMATION);
            }
            changed = input.useManualIncome(request.monthlyIncome());
        }
        if (!changed) return PlanInputResponse.from(input);
        historyRepository.save(previous);
        markAffectedStepsForRecalculation(planId);
        return PlanInputResponse.from(input);
    }

    public record OpenBankingIncomeSyncResult(OpenBankingIncomeSyncStatus status, PlanInputResponse input) {}

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

    private void validateFinancialSources(PlanInputRequest request, PlanInput input) {
        if (request.incomeSource() == FinancialValueSource.OPEN_BANKING
                && (input == null
                        || input.getIncomeSource() != FinancialValueSource.OPEN_BANKING
                        || !Objects.equals(input.getMonthlyIncome(), request.monthlyIncome()))) {
            throw new BusinessException(ErrorCode.INVALID_FINANCIAL_INCOME_CONFIRMATION);
        }
        if (request.assetSource() == FinancialValueSource.OPEN_BANKING
                && (input == null
                        || input.getAssetSource() != FinancialValueSource.OPEN_BANKING
                        || !Objects.equals(input.getNetAssets(), request.netAssets()))) {
            throw new BusinessException(ErrorCode.INVALID_FINANCIAL_INCOME_CONFIRMATION);
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
            case HOUSEHOLD_HOMELESS -> request.householdHomeless();
            case LIVES_APART_FROM_PARENTS -> request.livesApartFromParents();
            case PARENT_ON_HOUSING_BENEFIT -> request.parentOnHousingBenefit();
            case BIRTH_DATE -> request.birthDate();
            case MILITARY_MONTHS -> request.militaryMonths();
            case MONTHLY_INCOME -> request.monthlyIncome();
            case NET_ASSETS -> request.netAssets();
            case AVAILABLE_CASH -> request.availableCash();
            case EXISTING_JEONSE_LOAN -> request.existingJeonseLoan();
            case INCOME_SOURCE -> request.incomeSource();
            case ASSET_SOURCE -> request.assetSource();
            case FINANCIAL_DATA_CONFIRMED -> request.financialDataConfirmed();
        };
    }
}
