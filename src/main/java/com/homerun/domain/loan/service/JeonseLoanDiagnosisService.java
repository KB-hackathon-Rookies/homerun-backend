package com.homerun.domain.loan.service;

import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.loan.dto.response.JeonseLoanDiagnosisResponse;
import com.homerun.domain.loan.dto.response.LoanProductDiagnosisResponse;
import com.homerun.domain.loan.type.JeonseLoanProduct;
import com.homerun.domain.loan.type.LoanDiagnosisStatus;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.HouseholderStatus;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JeonseLoanDiagnosisService {

    private static final String YOUTH_SOURCE = "https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020301.jsp";
    private static final String GENERAL_SOURCE = "https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020101.jsp";
    private static final String DISCLAIMER = "예상 진단이며 실제 한도·금리·승인 여부는 주택과 보증기관 심사 및 은행 사전상담에서 달라질 수 있습니다.";

    private final PlanRepository planRepository;
    private final PlanInputRepository inputRepository;
    private final FactRegistry facts;
    private final Clock clock;

    public JeonseLoanDiagnosisService(
            PlanRepository planRepository, PlanInputRepository inputRepository, FactRegistry facts, Clock clock) {
        this.planRepository = planRepository;
        this.inputRepository = inputRepository;
        this.facts = facts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public JeonseLoanDiagnosisResponse diagnose(Long memberId, Long planId) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        if (plan.getLeaseType() == LeaseType.WOLSE) {
            throw new BusinessException(ErrorCode.JEONSE_PLAN_REQUIRED);
        }
        PlanInput input = inputRepository
                .findByPlanId(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_INPUT_NOT_FOUND));

        LoanProductDiagnosisResponse youth = youth(input);
        LoanProductDiagnosisResponse general = general(input);
        LoanProductDiagnosisResponse bank = bank(input);
        boolean incomplete = List.of(youth, general, bank).stream()
                .anyMatch(result -> result.status() == LoanDiagnosisStatus.REVIEW_REQUIRED);

        Long recommendedDeposit = youth.status() == LoanDiagnosisStatus.INELIGIBLE ? null : youth.depositLimit();
        Long estimatedLoan = youth.status() == LoanDiagnosisStatus.INELIGIBLE ? null : youth.estimatedLoanAmount();
        return new JeonseLoanDiagnosisResponse(
                planId,
                input.getHopeDeposit(),
                input.getAvailableCash(),
                recommendedDeposit,
                estimatedLoan,
                incomplete,
                List.of(youth, general, bank),
                DISCLAIMER,
                Instant.now(clock));
    }

    private LoanProductDiagnosisResponse youth(PlanInput input) {
        long incomeCap = facts.won("FCT-003");
        Fact assetCapFact = facts.require("FCT-170");
        Fact loanCapFact = facts.require("FCT-171");
        Fact depositCapFact = facts.require("FCT-175");
        BigDecimal loanRatio = facts.require("FCT-008").requireNumber();
        Fact rateMinFact = facts.require("FCT-172");
        Fact rateMaxFact = facts.require("FCT-173");
        long assetCap = assetCapFact.requireWon();
        long loanCap = loanCapFact.requireWon();
        long depositCap = depositCapFact.requireWon();
        BigDecimal rateMin = rateMinFact.requireNumber();
        BigDecimal rateMax = rateMaxFact.requireNumber();
        boolean provisional = List.of(assetCapFact, loanCapFact, depositCapFact, rateMinFact, rateMaxFact).stream()
                .anyMatch(Fact::provisional);

        List<String> blockers = commonBlockers(input);
        List<String> missing = commonMissing(input);
        if (input.getMonthlyIncome() != null && annualIncome(input) > incomeCap) {
            blockers.add("부부합산 추정 연소득이 청년 버팀목 기준을 초과합니다.");
        }
        if (input.getNetAssets() != null && input.getNetAssets() > assetCap) {
            blockers.add("순자산이 청년 버팀목 기준을 초과합니다.");
        }
        if (input.getBirthDate() == null) {
            missing.add("연령 확인을 위해 생년월일이 필요합니다.");
        } else if (!youthAgeEligible(input)) {
            blockers.add("병역 복무기간을 반영한 청년 연령 요건에 해당하지 않습니다.");
        }

        LoanDiagnosisStatus status = status(blockers, missing);
        Long target = input.getHopeDeposit();
        Long estimatedLoan = status == LoanDiagnosisStatus.INELIGIBLE || target == null
                ? null
                : Math.min(percent(target, loanRatio), loanCap);
        Long ownFunds = estimatedLoan == null || target == null ? null : Math.max(0, target - estimatedLoan);
        Long recommendedDeposit = status == LoanDiagnosisStatus.INELIGIBLE || input.getAvailableCash() == null
                ? null
                : Math.min(
                        depositCap,
                        BigDecimal.valueOf(input.getAvailableCash())
                                .add(BigDecimal.valueOf(loanCap))
                                .min(BigDecimal.valueOf(Long.MAX_VALUE))
                                .longValueExact());

        return response(
                JeonseLoanProduct.YOUTH_BEOTIMMOK,
                status,
                recommendedDeposit,
                estimatedLoan,
                ownFunds,
                rateMin,
                rateMax,
                provisional,
                messages(blockers, missing),
                YOUTH_SOURCE);
    }

    private LoanProductDiagnosisResponse general(PlanInput input) {
        List<String> blockers = commonBlockers(input);
        List<String> missing = commonMissing(input);
        if (blockers.isEmpty()) {
            missing.add("일반 버팀목의 가구별 소득·자산·보증 한도는 은행 사전심사에서 확인해야 합니다.");
        }
        return response(
                JeonseLoanProduct.GENERAL_BEOTIMMOK,
                status(blockers, missing),
                null,
                null,
                null,
                null,
                null,
                true,
                messages(blockers, missing),
                GENERAL_SOURCE);
    }

    private LoanProductDiagnosisResponse bank(PlanInput input) {
        List<String> blockers = new ArrayList<>();
        if (Boolean.TRUE.equals(input.getExistingJeonseLoan())) {
            blockers.add("기존 전세자금대출의 중복 이용 가능 여부를 먼저 확인해야 합니다.");
        }
        List<String> review =
                blockers.isEmpty() ? List.of("은행 상품은 보증기관과 매물 심사에 따라 한도와 금리가 달라 사전상담이 필요합니다.") : List.of();
        return response(
                JeonseLoanProduct.BANK_JEONSE_LOAN,
                status(blockers, review),
                null,
                null,
                null,
                null,
                null,
                true,
                messages(blockers, review),
                null);
    }

    private List<String> commonBlockers(PlanInput input) {
        List<String> blockers = new ArrayList<>();
        Boolean homeless = input.getHouseholdHomeless() != null ? input.getHouseholdHomeless() : input.getHomeless();
        if (Boolean.FALSE.equals(homeless)) {
            blockers.add("세대원 중 주택 보유자가 있어 무주택 요건을 충족하지 않습니다.");
        }
        if (input.getHouseholderStatus() == HouseholderStatus.NOT_HOUSEHOLDER) {
            blockers.add("세대주 또는 대출 후 분가 예정 요건을 충족하지 않습니다.");
        }
        if (Boolean.TRUE.equals(input.getExistingJeonseLoan())) {
            blockers.add("기존 전세자금대출이 있어 중복 대출 여부 확인이 필요합니다.");
        }
        return blockers;
    }

    private List<String> commonMissing(PlanInput input) {
        List<String> missing = new ArrayList<>();
        if (input.getHouseholdHomeless() == null && input.getHomeless() == null) {
            missing.add("세대원 전원 무주택 여부가 필요합니다.");
        }
        if (input.getHouseholderStatus() == null) {
            missing.add("세대주 또는 분가 예정 여부가 필요합니다.");
        }
        if (input.getMonthlyIncome() == null) {
            missing.add("월소득 확인이 필요합니다.");
        }
        if (input.getNetAssets() == null) {
            missing.add("순자산 확인이 필요합니다.");
        }
        if (input.getHopeDeposit() == null) {
            missing.add("최대 희망 보증금이 필요합니다.");
        }
        if (input.getAvailableCash() == null) {
            missing.add("사용 가능한 자기자금이 필요합니다.");
        }
        if (!Boolean.TRUE.equals(input.getFinancialDataConfirmed())) {
            missing.add("조회하거나 직접 입력한 금융정보를 확인해 주세요.");
        }
        return missing;
    }

    private boolean youthAgeEligible(PlanInput input) {
        LocalDate today = LocalDate.now(clock);
        int militaryMonths = input.getMilitaryMonths() == null ? 0 : Math.min(input.getMilitaryMonths(), 60);
        int maximumAge = facts.require("FCT-174").requireNumber().intValueExact();
        LocalDate eligibleUntil =
                input.getBirthDate().plusYears(maximumAge + 1L).plusMonths(militaryMonths);
        return !today.isBefore(input.getBirthDate().plusYears(19)) && today.isBefore(eligibleUntil);
    }

    private long annualIncome(PlanInput input) {
        return Math.multiplyExact(input.getMonthlyIncome(), 12L);
    }

    private long percent(long amount, BigDecimal percent) {
        return BigDecimal.valueOf(amount)
                .multiply(percent)
                .divide(new BigDecimal("100"), 0, RoundingMode.DOWN)
                .longValueExact();
    }

    private Long monthlyInterest(Long loan, BigDecimal rate) {
        if (loan == null || rate == null) {
            return null;
        }
        return BigDecimal.valueOf(loan)
                .multiply(rate)
                .divide(new BigDecimal("1200"), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private LoanDiagnosisStatus status(List<String> blockers, List<String> review) {
        if (!blockers.isEmpty()) {
            return LoanDiagnosisStatus.INELIGIBLE;
        }
        return review.isEmpty() ? LoanDiagnosisStatus.EXPECTED_ELIGIBLE : LoanDiagnosisStatus.REVIEW_REQUIRED;
    }

    private List<String> messages(List<String> blockers, List<String> review) {
        List<String> messages = new ArrayList<>(blockers);
        messages.addAll(review);
        return messages;
    }

    private LoanProductDiagnosisResponse response(
            JeonseLoanProduct product,
            LoanDiagnosisStatus status,
            Long depositLimit,
            Long loan,
            Long ownFunds,
            BigDecimal rateMin,
            BigDecimal rateMax,
            boolean criteriaProvisional,
            List<String> reasons,
            String sourceUrl) {
        return new LoanProductDiagnosisResponse(
                product,
                product.displayName(),
                status,
                depositLimit,
                loan,
                ownFunds,
                rateMin,
                rateMax,
                monthlyInterest(loan, rateMin),
                monthlyInterest(loan, rateMax),
                criteriaProvisional,
                reasons,
                sourceUrl);
    }
}
