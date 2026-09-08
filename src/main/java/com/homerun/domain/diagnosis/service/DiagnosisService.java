package com.homerun.domain.diagnosis.service;

import com.homerun.domain.diagnosis.dto.request.DiagnosisCalculationRequest;
import com.homerun.domain.diagnosis.dto.response.DiagnosisCostResponse;
import com.homerun.domain.diagnosis.dto.response.DiagnosisPolicyComparisonResponse;
import com.homerun.domain.diagnosis.dto.response.DiagnosisResponse;
import com.homerun.domain.diagnosis.entity.CostEstimate;
import com.homerun.domain.diagnosis.entity.Diagnosis;
import com.homerun.domain.diagnosis.model.DiagnosisOverrides;
import com.homerun.domain.diagnosis.repository.CostEstimateRepository;
import com.homerun.domain.diagnosis.repository.DiagnosisRepository;
import com.homerun.domain.diagnosis.type.DiagnosisVerdict;
import com.homerun.domain.diagnosis.type.DiagnosisWarning;
import com.homerun.domain.openbanking.entity.FinancialSnapshot;
import com.homerun.domain.openbanking.repository.FinancialSnapshotRepository;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.FinancialValueSource;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.policy.dto.response.AncillaryCostResponse;
import com.homerun.domain.policy.service.AncillaryCostCalculator;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosisService {

    static final String ENGINE_VERSION = "DIA-02-1.0";

    private final PlanRepository plans;
    private final PlanInputRepository inputs;
    private final FinancialSnapshotRepository snapshots;
    private final CostEstimateRepository costs;
    private final DiagnosisRepository diagnoses;
    private final AncillaryCostCalculator ancillaryCosts;
    private final Clock clock;

    public DiagnosisService(
            PlanRepository plans,
            PlanInputRepository inputs,
            FinancialSnapshotRepository snapshots,
            CostEstimateRepository costs,
            DiagnosisRepository diagnoses,
            AncillaryCostCalculator ancillaryCosts,
            Clock clock) {
        this.plans = plans;
        this.inputs = inputs;
        this.snapshots = snapshots;
        this.costs = costs;
        this.diagnoses = diagnoses;
        this.ancillaryCosts = ancillaryCosts;
        this.clock = clock;
    }

    @Transactional
    public DiagnosisResponse calculate(Long memberId, Long planId, DiagnosisCalculationRequest request) {
        Calculation calculation = compute(memberId, planId, request, DiagnosisOverrides.none());
        CostEstimate cost = costs.save(calculation.cost());
        Diagnosis diagnosis = diagnoses.save(calculation.toDiagnosis(cost.getId()));
        return response(diagnosis, cost);
    }

    @Transactional(readOnly = true)
    public DiagnosisResponse simulate(Long memberId, Long planId, DiagnosisCalculationRequest request) {
        return simulate(memberId, planId, request, DiagnosisOverrides.none());
    }

    /**
     * 계획을 고치지 않고 축(희망 보증금·독립 희망일)만 바꿔 계산한다(ALT-01-02).
     *
     * <p>저장하지 않는 것은 {@link #simulate(Long, Long, DiagnosisCalculationRequest)} 과 같다.
     * override 가 비어 있으면 계산 결과도 그것과 완전히 같다.
     */
    @Transactional(readOnly = true)
    public DiagnosisResponse simulate(
            Long memberId, Long planId, DiagnosisCalculationRequest request, DiagnosisOverrides overrides) {
        Calculation calculation = compute(memberId, planId, request, overrides);
        return response(calculation.toDiagnosis(null), calculation.cost());
    }

    @Transactional(readOnly = true)
    public DiagnosisResponse latest(Long memberId, Long planId) {
        ownedJeonsePlan(memberId, planId);
        Diagnosis diagnosis = diagnoses
                .findFirstByPlanIdAndCostEstimateIdIsNotNullOrderByCreatedAtDescIdDesc(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DIAGNOSIS_NOT_FOUND));
        CostEstimate cost = costs.findById(diagnosis.getCostEstimateId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DIAGNOSIS_NOT_FOUND));
        return response(diagnosis, cost);
    }

    @Transactional(readOnly = true)
    public DiagnosisResponse get(Long memberId, Long planId, Long diagnosisId) {
        ownedJeonsePlan(memberId, planId);
        Diagnosis diagnosis = diagnoses
                .findByIdAndPlanId(diagnosisId, planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DIAGNOSIS_NOT_FOUND));
        CostEstimate cost = costs.findById(diagnosis.getCostEstimateId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DIAGNOSIS_NOT_FOUND));
        return response(diagnosis, cost);
    }

    private Calculation compute(
            Long memberId, Long planId, DiagnosisCalculationRequest request, DiagnosisOverrides overrides) {
        Plan plan = ownedJeonsePlan(memberId, planId);
        PlanInput input =
                inputs.findByPlanId(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_INPUT_NOT_FOUND));
        requireCoreInput(input);

        LocalDate today = LocalDate.now(clock);
        Instant calculatedAt = Instant.now(clock);
        List<DiagnosisWarning> warnings = new ArrayList<>();
        int monthsToMove = monthsToMove(today, overrides.targetMoveDateOr(plan.getTargetMoveDate()), warnings);
        long monthlyDebtPayment = monthlyDebtPayment(memberId, request.monthlyDebtPayment(), warnings);
        if (input.getIncomeSource() == FinancialValueSource.OPEN_BANKING
                && !Boolean.TRUE.equals(input.getFinancialDataConfirmed())) {
            warnings.add(DiagnosisWarning.OPEN_BANKING_INCOME_UNCONFIRMED);
        }

        try {
            long deposit = overrides.hopeDepositOr(input.getHopeDeposit());
            ResolvedCosts resolved = resolveCosts(deposit, request, warnings);
            long nonDepositCost = add(
                    resolved.movingCost(),
                    resolved.brokerageFee(),
                    resolved.guaranteeFee(),
                    resolved.stampTax(),
                    resolved.emergencyReserve());
            long totalRequired = add(deposit, nonDepositCost);
            long ownDeposit = Math.max(0, deposit - request.expectedLoanAmount());
            long requiredCashAfterPolicy = add(ownDeposit, nonDepositCost);

            long monthlyHousingCostBeforePolicy = add(zero(input.getMonthlyRent()), zero(input.getMaintenanceFee()));
            long monthlyHousingCost = add(monthlyHousingCostBeforePolicy, request.expectedMonthlyInterest());
            long nonHousingOutflow = add(resolved.monthlyLivingExpense(), monthlyDebtPayment);
            long monthlyDisposableBeforePolicy = Math.subtractExact(
                    input.getMonthlyIncome(), add(monthlyHousingCostBeforePolicy, nonHousingOutflow));
            long monthlyDisposable =
                    Math.subtractExact(input.getMonthlyIncome(), add(monthlyHousingCost, nonHousingOutflow));
            long savableAmount = Math.multiplyExact(Math.max(0, monthlyDisposable), monthsToMove);
            long returnableDeposit = zero(input.getCurrentDeposit());
            long expectedFund = add(input.getAvailableCash(), returnableDeposit, savableAmount);
            long shortfall = Math.max(0, Math.subtractExact(requiredCashAfterPolicy, expectedFund));
            long currentFund = add(input.getAvailableCash(), returnableDeposit);
            LocalDate possibleDateBeforePolicy =
                    possibleDate(today, totalRequired, currentFund, monthlyDisposableBeforePolicy);
            LocalDate possibleDate = possibleDate(today, requiredCashAfterPolicy, currentFund, monthlyDisposable);
            DiagnosisVerdict verdict = verdict(shortfall, monthlyDisposable, warnings);

            CostEstimate cost = new CostEstimate(
                    planId,
                    deposit,
                    resolved.movingCost(),
                    resolved.brokerageFee(),
                    resolved.guaranteeFee(),
                    resolved.stampTax(),
                    resolved.emergencyReserve(),
                    totalRequired,
                    calculatedAt);
            return new Calculation(
                    planId,
                    input.getAvailableCash(),
                    returnableDeposit,
                    input.getMonthlyIncome(),
                    monthlyHousingCost,
                    resolved.monthlyLivingExpense(),
                    monthlyDebtPayment,
                    monthlyDisposable,
                    monthsToMove,
                    savableAmount,
                    expectedFund,
                    request.expectedLoanAmount(),
                    request.expectedMonthlyInterest(),
                    requiredCashAfterPolicy,
                    shortfall,
                    possibleDateBeforePolicy,
                    possibleDate,
                    verdict,
                    List.copyOf(warnings),
                    calculatedAt,
                    cost);
        } catch (ArithmeticException | DateTimeException exception) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_CALCULATION_OVERFLOW, exception);
        }
    }

    private Plan ownedJeonsePlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        if (plan.getLeaseType() != LeaseType.JEONSE && plan.getLeaseType() != LeaseType.BANJEONSE) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_JEONSE_PLAN_REQUIRED);
        }
        return plan;
    }

    private void requireCoreInput(PlanInput input) {
        if (input.getHopeDeposit() == null || input.getMonthlyIncome() == null || input.getAvailableCash() == null) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_INPUT_REQUIRED);
        }
    }

    private int monthsToMove(LocalDate today, LocalDate target, List<DiagnosisWarning> warnings) {
        if (target == null) {
            warnings.add(DiagnosisWarning.TARGET_MOVE_DATE_MISSING);
            return 0;
        }
        long months = Math.max(0, ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(target)));
        return Math.toIntExact(months);
    }

    /** 계산에 실제로 들어간 비용. 사용자가 준 값이거나, 서버가 기준 수치로 계산한 값이다. */
    private record ResolvedCosts(
            long movingCost,
            long brokerageFee,
            long guaranteeFee,
            long stampTax,
            long emergencyReserve,
            long monthlyLivingExpense) {}

    /**
     * 비어 있는 비용을 채운다.
     *
     * <p>0 은 "확인해서 0원" 이고 null 은 "모른다" 다. 전에는 이 둘을 구분할 수 없어 화면이 모르는
     * 값까지 0 으로 보냈고, 그 0 이 그대로 계산에 들어가 <b>초기 필요자금과 부족자금이 실제보다
     * 작게, 월 여유자금이 실제보다 크게</b> 나왔다.
     *
     * <p>중개보수·인지세·보증료·이사비는 대출금이 정해져야 나오는 값이라 화면이 알 수 없다. 그래서
     * 여기서 {@code config_effective} 기준으로 계산한다(BR-08a·BR-27). 담보가 아직 정해지지 않은
     * 시점이라 보증료율은 중간값 추정이다.
     *
     * <p>생활비와 예비비는 계산할 근거가 없다. 지어내지 않고 0 으로 두되 경고를 남긴다 — 결과가
     * 낙관적으로 치우쳤다는 것을 화면이 말할 수 있어야 한다.
     */
    private ResolvedCosts resolveCosts(
            long deposit, DiagnosisCalculationRequest request, List<DiagnosisWarning> warnings) {
        boolean needsEstimate = request.movingCost() == null
                || request.brokerageFee() == null
                || request.guaranteeFee() == null
                || request.stampTax() == null;
        AncillaryCostResponse estimate = null;
        if (needsEstimate) {
            // 담보(collateral)·아파트 여부는 2루에서 정해진다. 여기서는 아직 모르므로 넘기지 않는다.
            estimate = ancillaryCosts.ancillaryCost(
                    deposit, request.expectedLoanAmount(), null, null, request.movingCost());
            warnings.add(DiagnosisWarning.ANCILLARY_COST_ESTIMATED);
        }

        long brokerage;
        if (request.brokerageFee() != null) {
            brokerage = request.brokerageFee();
        } else if (estimate.brokerageFee() != null) {
            brokerage = estimate.brokerageFee();
        } else {
            // 요율표를 확보하지 못한 구간(보증금 6억 초과)이다. 지어내지 않고 빠졌다고 말한다.
            warnings.add(DiagnosisWarning.BROKERAGE_FEE_UNKNOWN);
            brokerage = 0;
        }

        return new ResolvedCosts(
                request.movingCost() != null ? request.movingCost() : estimate.movingCost(),
                brokerage,
                request.guaranteeFee() != null ? request.guaranteeFee() : estimate.guaranteeFee(),
                request.stampTax() != null ? request.stampTax() : estimate.stampDuty(),
                missingIsZero(request.emergencyReserve(), DiagnosisWarning.EMERGENCY_RESERVE_MISSING, warnings),
                missingIsZero(
                        request.monthlyLivingExpense(), DiagnosisWarning.MONTHLY_LIVING_EXPENSE_MISSING, warnings));
    }

    /** 계산할 근거가 없는 값. 0 으로 두되 모른다는 것을 남긴다. */
    private long missingIsZero(Long value, DiagnosisWarning missing, List<DiagnosisWarning> warnings) {
        if (value != null) {
            return value;
        }
        warnings.add(missing);
        return 0;
    }

    private long monthlyDebtPayment(Long memberId, Long requestValue, List<DiagnosisWarning> warnings) {
        if (requestValue != null) {
            return requestValue;
        }
        Long snapshotValue = snapshots
                .findFirstByUserIdOrderByCreatedAtDescIdDesc(memberId)
                .map(FinancialSnapshot::getMonthlyDebtPayment)
                .orElse(null);
        warnings.add(DiagnosisWarning.MONTHLY_DEBT_PAYMENT_UNCONFIRMED);
        return zero(snapshotValue);
    }

    private LocalDate possibleDate(LocalDate today, long required, long currentFund, long monthlyDisposable) {
        long remaining = Math.max(0, Math.subtractExact(required, currentFund));
        if (remaining == 0) {
            return today;
        }
        if (monthlyDisposable <= 0) {
            return null;
        }
        long months = Math.floorDiv(Math.subtractExact(remaining, 1), monthlyDisposable) + 1;
        return today.plusMonths(months);
    }

    private DiagnosisVerdict verdict(long shortfall, long monthlyDisposable, List<DiagnosisWarning> warnings) {
        if (monthlyDisposable < 0 || (shortfall > 0 && monthlyDisposable == 0)) {
            return DiagnosisVerdict.DIFFICULT;
        }
        if (shortfall > 0 || !warnings.isEmpty()) {
            return DiagnosisVerdict.CAUTION;
        }
        return DiagnosisVerdict.POSSIBLE;
    }

    private long add(long... values) {
        long result = 0;
        for (long value : values) {
            result = Math.addExact(result, value);
        }
        return result;
    }

    private long zero(Long value) {
        return value == null ? 0 : value;
    }

    private DiagnosisResponse response(Diagnosis diagnosis, CostEstimate cost) {
        long reduced = Math.subtractExact(cost.getTotalRequired(), diagnosis.getRequiredCashAfterPolicy());
        return new DiagnosisResponse(
                diagnosis.getId(),
                diagnosis.getPlanId(),
                new DiagnosisCostResponse(
                        cost.getDeposit(),
                        cost.getMovingCost(),
                        cost.getBrokerageFee(),
                        cost.getGuaranteeFee(),
                        cost.getStampTax(),
                        cost.getEmergencyReserve(),
                        cost.getTotalRequired()),
                new DiagnosisPolicyComparisonResponse(
                        cost.getTotalRequired(),
                        diagnosis.getRequiredCashAfterPolicy(),
                        reduced,
                        diagnosis.getExpectedLoanAmount(),
                        diagnosis.getExpectedMonthlyInterest(),
                        diagnosis.getMonthlyHousingCost() - diagnosis.getExpectedMonthlyInterest(),
                        diagnosis.getMonthlyHousingCost(),
                        diagnosis.getMonthlyDisposable() + diagnosis.getExpectedMonthlyInterest(),
                        diagnosis.getMonthlyDisposable(),
                        diagnosis.getPossibleDateBeforePolicy(),
                        diagnosis.getPossibleDate()),
                diagnosis.getMonthlyIncome(),
                diagnosis.getMonthlyHousingCost(),
                diagnosis.getMonthlyLivingExpense(),
                diagnosis.getMonthlyDebtPayment(),
                diagnosis.getMonthlyDisposable(),
                diagnosis.getMonthsToMove(),
                diagnosis.getSavableAmount(),
                diagnosis.getExpectedFund(),
                diagnosis.getShortfall(),
                diagnosis.getPossibleDate(),
                diagnosis.getVerdict(),
                diagnosis.getWarnings(),
                diagnosis.getEngineVersion(),
                diagnosis.getCreatedAt());
    }

    private record Calculation(
            Long planId,
            long availableCash,
            long returnableDeposit,
            long monthlyIncome,
            long monthlyHousingCost,
            long monthlyLivingExpense,
            long monthlyDebtPayment,
            long monthlyDisposable,
            int monthsToMove,
            long savableAmount,
            long expectedFund,
            long expectedLoanAmount,
            long expectedMonthlyInterest,
            long requiredCashAfterPolicy,
            long shortfall,
            LocalDate possibleDateBeforePolicy,
            LocalDate possibleDate,
            DiagnosisVerdict verdict,
            List<DiagnosisWarning> warnings,
            Instant calculatedAt,
            CostEstimate cost) {

        Diagnosis toDiagnosis(Long costEstimateId) {
            return new Diagnosis(
                    planId,
                    costEstimateId,
                    availableCash,
                    returnableDeposit,
                    monthlyIncome,
                    monthlyHousingCost,
                    monthlyLivingExpense,
                    monthlyDebtPayment,
                    monthlyDisposable,
                    monthsToMove,
                    savableAmount,
                    expectedFund,
                    expectedLoanAmount,
                    expectedMonthlyInterest,
                    requiredCashAfterPolicy,
                    shortfall,
                    possibleDateBeforePolicy,
                    possibleDate,
                    verdict,
                    warnings,
                    ENGINE_VERSION,
                    calculatedAt);
        }
    }
}
