package com.homerun.domain.policy.service;

import com.homerun.domain.fact.exception.FactNotFoundException;
import com.homerun.domain.fact.exception.UnusableFactException;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.domain.policy.model.ConditionResult;
import com.homerun.domain.policy.model.ExpectedEstimate;
import com.homerun.domain.policy.model.RuleCondition;
import com.homerun.domain.policy.model.RuleDocument;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.region.service.PolicyRegionResolver;
import com.homerun.domain.region.type.PolicyArea;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * {@code policy_rule.rule_json} 조건식을 읽어 {@code plan_input} 과 대조하는 인터프리터.
 *
 * <p>모르는 {@code op} 나 못 구한 팩트는 전부 NEED_INFO 로 떨어뜨린다 — 여기서 기본값을 채워
 * 넘기면 그게 곧 틀린 안내다(NFR-01-06). {@code plan_input} 기반 조건이 기본이고, 매물별
 * 조건(반환보증 공시가격 룰 등)은 {@link #evaluate(RuleDocument, PlanInput, Property)} 로
 * property 를 함께 넘겨야 풀린다(#89).
 */
@Service
public class PolicyRuleEngine {

    /** factCode 가 없는 조건(무주택·세대주 등)의 화면 표시 문구. rule_json 에 사람이 읽을 라벨이
     * 없어서 여기 최소한으로 둔다 — 조건이 늘어나면 이 표도 늘어난다. */
    private static final Map<String, String> STATIC_LABELS = Map.of(
            "HOUSEHOLD_HOMELESS", "세대원 전원 무주택",
            "HOUSEHOLDER_STATUS", "세대주 또는 예비 세대주",
            "NO_DUPLICATE_LOAN", "기존 전세자금대출 없음",
            "NOT_VIOLATION_BUILDING", "위반건축물이 아님",
            "NOT_MULTI_HOUSEHOLD", "다가구 주택이 아님",
            "REGION_TARGET", "희망 지역이 서울");

    private final FactRegistry facts;
    private final Clock clock;
    private final PolicyRegionResolver regions;

    public PolicyRuleEngine(FactRegistry facts, Clock clock, PolicyRegionResolver regions) {
        this.facts = facts;
        this.clock = clock;
        this.regions = regions;
    }

    /** plan_input 기반 조건만 쓰는 정책(청년/일반 버팀목, 서울시 이자지원)용. */
    public List<ConditionResult> evaluate(RuleDocument document, PlanInput input) {
        return evaluate(document, input, null);
    }

    /** property 기반 조건(반환보증 공시가격 룰 등)까지 쓸 때는 property 를 함께 넘긴다. */
    public List<ConditionResult> evaluate(RuleDocument document, PlanInput input, Property property) {
        List<ConditionResult> results = new ArrayList<>();
        for (RuleCondition condition : document.conditions()) {
            results.add(evaluateOne(condition, input, property));
        }
        return results;
    }

    /**
     * 조건 판정과 별개로 예상 대출 스펙을 계산한다({@code #95} — 원래 {@code #80} 하드코딩
     * 서비스가 하던 계산을 여기로 옮겼다). 금액과 금리의 확인 가능 여부는 분리한다.
     * 금리 미확인은 금리·이자만 비우며, 금액 계산에 필요한 기준이 없거나 FAIL이면 전부 비운다.
     */
    public ExpectedEstimate estimate(
            RuleDocument document,
            List<ConditionResult> conditionResults,
            PlanInput input,
            PolicyVerdictResult verdict) {
        if (document.amount() == null
                || input == null
                || input.getHopeDeposit() == null
                || verdict == PolicyVerdictResult.FAIL) {
            return ExpectedEstimate.empty();
        }

        Optional<Fact> ratioFact = resolveFact(document.amount().ratioFactCode());
        String capCode = document.amount().capFactCode();
        if (document.amount().nonCapitalCapFactCode() != null) {
            PolicyArea area = regions.resolve(input.getRegionId());
            if (area == null) return ExpectedEstimate.empty();
            if (area == PolicyArea.NON_CAPITAL) capCode = document.amount().nonCapitalCapFactCode();
        }
        Optional<Fact> capFact = resolveFact(capCode);
        if (ratioFact.isEmpty() || capFact.isEmpty()) {
            return ExpectedEstimate.empty();
        }

        long hopeDeposit = input.getHopeDeposit();
        long loanCap = capFact.get().requireWon();
        BigDecimal ratio = ratioFact.get().requireNumber();
        if (hopeDeposit < 0 || loanCap < 0 || ratio.signum() < 0 || ratio.compareTo(new BigDecimal("100")) > 0) {
            return ExpectedEstimate.empty();
        }
        long estimatedLoan = Math.min(percent(hopeDeposit, ratio), loanCap);
        long ownFunds = Math.max(0, hopeDeposit - estimatedLoan);
        BigDecimal rateMin = document.rate() == null
                ? null
                : resolveFact(document.rate().minFactCode()).map(Fact::number).orElse(null);
        BigDecimal rateMax = document.rate() == null
                ? null
                : resolveFact(document.rate().maxFactCode()).map(Fact::number).orElse(null);
        if (rateMin == null || rateMax == null || rateMin.signum() < 0 || rateMin.compareTo(rateMax) > 0) {
            rateMin = null;
            rateMax = null;
        }

        return new ExpectedEstimate(
                recommendedDeposit(conditionResults, input, loanCap, ratio),
                estimatedLoan,
                ownFunds,
                rateMin,
                rateMax,
                monthlyInterest(estimatedLoan, rateMin),
                monthlyInterest(estimatedLoan, rateMax));
    }

    /** DEPOSIT_CAP 조건이 이미 참조하는 fact_code 를 그대로 재사용한다 — amount 스펙에 따로
     * 안 두고 조건식에서 읽어서, 같은 상한을 두 군데서 따로 관리하지 않는다. */
    private Long recommendedDeposit(
            List<ConditionResult> conditionResults, PlanInput input, long loanCap, BigDecimal ratio) {
        if (input.getAvailableCash() == null || input.getAvailableCash() < 0) {
            return null;
        }
        BigDecimal cash = BigDecimal.valueOf(input.getAvailableCash());
        BigDecimal limit = cash.add(BigDecimal.valueOf(loanCap)).min(BigDecimal.valueOf(Long.MAX_VALUE));
        BigDecimal ownRatio = new BigDecimal("100").subtract(ratio);
        if (ownRatio.signum() > 0) {
            // 대출 비율이 80%라면 나머지 20%는 현금으로 충당해야 한다.
            limit = limit.min(cash.multiply(new BigDecimal("100")).divide(ownRatio, 0, RoundingMode.DOWN));
        }
        for (ConditionResult condition : conditionResults) {
            if (!"DEPOSIT_CAP".equals(condition.code())) {
                continue;
            }
            Optional<Fact> depositCap = resolveFact(condition.factCode());
            if (depositCap.isEmpty()
                    || depositCap.get().number() == null
                    || depositCap.get().number().signum() < 0) {
                return null;
            }
            limit = limit.min(depositCap.get().number());
        }
        return limit.setScale(0, RoundingMode.DOWN).longValueExact();
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

    private long percent(long amount, BigDecimal percent) {
        return BigDecimal.valueOf(amount)
                .multiply(percent)
                .divide(new BigDecimal("100"), 0, RoundingMode.DOWN)
                .longValueExact();
    }

    private ConditionResult evaluateOne(RuleCondition condition, PlanInput input, Property property) {
        return switch (condition.op()) {
            case "eq" -> equalityCheck(condition, input, property, true);
            case "ne" -> equalityCheck(condition, input, property, false);
            case "lte" -> numericCheck(condition, input, false);
            case "lte_by_region" -> regionalLimit(condition, input);
            case "region_eq" -> regionEquals(condition, input);
            case "gte" -> numericCheck(condition, input, true);
            case "annual_lte" -> annualIncomeCheck(condition, input);
            case "annual_lte_by_age_group" -> annualLteByAgeGroup(condition, input);
            case "age_within_years_adjusted" -> ageWithinYearsAdjusted(condition, input);
            case "age_range_adjusted" -> ageRangeAdjusted(condition, input);
            case "annual_lte_by_employment_type" -> annualLteByEmploymentType(condition, input);
            case "deposit_lte_price_times_fact" -> depositWithinPriceRatio(condition, property);
            case "reference_only" -> referenceOnly(condition);
            default -> needInfo(condition, "이 조건은 자동판정 대상이 아닙니다. 원문을 직접 확인해야 합니다.");
        };
    }

    /** field 가 plan_input 소속인지 property 소속인지는 이름으로만 구분한다 — 이름이 겹치지
     * 않아서(#100) 조건식에 entity 를 따로 안 적어도 된다. */
    private ConditionResult equalityCheck(
            RuleCondition condition, PlanInput input, Property property, boolean expectEqual) {
        Object fieldValue = resolveField(condition.field(), input, property);
        if (fieldValue == null) {
            return needInfo(condition, null);
        }
        String actual = fieldValue instanceof Enum<?> enumValue ? enumValue.name() : String.valueOf(fieldValue);
        String expected = String.valueOf(condition.value());
        boolean equal = actual.equals(expected);
        return met(condition, expectEqual == equal, null);
    }

    /** field 가 BIGINT(금액 등)든 NUMERIC(면적 등)이든 상관없이 fact 와 비교한다 — 둘 다
     * plan_input 에 섞여 있어서(#102) 하나로 받는다. */
    private ConditionResult numericCheck(RuleCondition condition, PlanInput input, boolean gte) {
        BigDecimal amount = toComparable(resolveField(condition.field(), input));
        if (amount == null) {
            return needInfo(condition, null);
        }
        Optional<Fact> fact = resolveFact(condition.factCode());
        if (fact.isEmpty()) {
            return needInfo(condition, null);
        }
        int cmp = amount.compareTo(fact.get().requireNumber());
        boolean pass = gte ? cmp >= 0 : cmp <= 0;
        return met(condition, pass, fact.get());
    }

    private ConditionResult regionalLimit(RuleCondition condition, PlanInput input) {
        PolicyArea area = regions.resolve(input == null ? null : input.getRegionId());
        if (area == null) return needInfo(condition, "희망 지역을 확인해야 상한을 결정할 수 있습니다.");
        String factCode = area == PolicyArea.NON_CAPITAL ? condition.altFactCode() : condition.factCode();
        RuleCondition selected =
                new RuleCondition(condition.code(), condition.field(), "lte", condition.value(), factCode, null);
        return numericCheck(selected, input, false);
    }

    private ConditionResult regionEquals(RuleCondition condition, PlanInput input) {
        PolicyArea area = regions.resolve(input == null ? null : input.getRegionId());
        if (area == null) return needInfo(condition, "희망 지역을 확인해 주세요.");
        return met(
                condition,
                area.name().equals(String.valueOf(condition.value())),
                resolveFact(condition.factCode()).orElse(null));
    }

    private BigDecimal toComparable(Object fieldValue) {
        if (fieldValue instanceof Long amount) {
            return BigDecimal.valueOf(amount);
        }
        if (fieldValue instanceof BigDecimal amount) {
            return amount;
        }
        return null;
    }

    private ConditionResult annualIncomeCheck(RuleCondition condition, PlanInput input) {
        Object fieldValue = resolveField(condition.field(), input);
        if (fieldValue == null || !(fieldValue instanceof Long monthly)) {
            return needInfo(condition, null);
        }
        Optional<Fact> fact = resolveFact(condition.factCode());
        if (fact.isEmpty()) {
            return needInfo(condition, null);
        }
        long annual = Math.multiplyExact(monthly, 12L);
        boolean pass = BigDecimal.valueOf(annual).compareTo(fact.get().requireNumber()) <= 0;
        return met(condition, pass, fact.get());
    }

    /**
     * FCT-033 분해(GTE-01-04, #104). 혼인 여부에 따라 갈리는 소득 상한 — 신혼부부(혼인 7년
     * 이내)는 {@code plan_input}에 혼인 기간이 없어 항상 NEED_INFO 로 둔다(모르는 걸 청년/일반
     * 어느 쪽으로도 단정하지 않는다). 미혼일 때만 나이로 청년(factCode)/일반(altFactCode)을
     * 가른다.
     */
    private ConditionResult annualLteByAgeGroup(RuleCondition condition, PlanInput input) {
        if (input == null) {
            return needInfo(condition, null);
        }
        MaritalStatus maritalStatus = input.getMaritalStatus();
        if (maritalStatus == null) {
            return needInfo(condition, "혼인 여부를 확인해야 소득 기준을 알 수 있습니다.");
        }
        if (maritalStatus == MaritalStatus.MARRIED) {
            return needInfo(condition, "신혼부부(혼인 7년 이내) 여부를 확인해야 소득 기준을 알 수 있습니다.");
        }
        Long monthly = input.getMonthlyIncome();
        LocalDate birthDate = input.getBirthDate();
        if (monthly == null || birthDate == null) {
            return needInfo(condition, null);
        }
        LocalDate today = LocalDate.now(clock);
        boolean isYouthAge = !today.isBefore(birthDate.plusYears(19)) && today.isBefore(birthDate.plusYears(40));
        String factCode = isYouthAge ? condition.factCode() : condition.altFactCode();
        Optional<Fact> fact = resolveFact(factCode);
        if (fact.isEmpty()) {
            return needInfo(condition, null);
        }
        long annual = Math.multiplyExact(monthly, 12L);
        boolean pass = BigDecimal.valueOf(annual).compareTo(fact.get().requireNumber()) <= 0;
        return met(condition, pass, fact.get());
    }

    /**
     * FCT-002 (병역 보정: 만 34세 종료일 + 복무기간, 최대 만 39세) 를 그대로 옮긴다. 종료
     * 상한을 만 40세 생일 전까지로 못박아서, 복무기간이 길어도 그 이상은 인정하지 않는다.
     *
     * <p>{@code eligibleUntil} 을 결과에 실어 자격이 사라지는 날짜를 노출한다(POL-01-04, #110).
     * PASS 든 FAIL 이든 채운다 — 이미 지났어도 "언제 지났는지"는 여전히 의미 있는 정보다.
     */
    private ConditionResult ageWithinYearsAdjusted(RuleCondition condition, PlanInput input) {
        LocalDate birthDate = input == null ? null : input.getBirthDate();
        if (birthDate == null) {
            return needInfo(condition, null);
        }
        Optional<Fact> fact = resolveFact(condition.factCode());
        if (fact.isEmpty()) {
            return needInfo(condition, null);
        }
        int maxAge = fact.get().requireNumber().intValueExact();
        long adjustMonths = "military_months".equals(condition.adjustField()) && input.getMilitaryMonths() != null
                ? Math.max(0, input.getMilitaryMonths())
                : 0L;
        LocalDate today = LocalDate.now(clock);
        LocalDate baseUntil = birthDate.plusYears(maxAge + 1L);
        LocalDate militaryAdjustedUntil = baseUntil.plusMonths(adjustMonths);
        LocalDate hardCap = birthDate.plusYears(40L);
        LocalDate eligibleUntil = militaryAdjustedUntil.isAfter(hardCap) ? hardCap : militaryAdjustedUntil;
        boolean pass = !today.isBefore(birthDate.plusYears(19)) && today.isBefore(eligibleUntil);
        return met(condition, pass, fact.get(), eligibleUntil);
    }

    /**
     * FCT-085 분해(POL-02-02, #116). {@link #ageWithinYearsAdjusted}와 다르게 상한 하나가
     * 아니라 하한도 있다 — factCode 를 하한, altFactCode 를 상한으로 재사용한다(둘 다 동시에
     * 쓰는 경우라 annual_lte_by_age_group 의 "양자택일" 용법과는 다르다).
     *
     * <p>"병역 이행기간 제외"는 상한을 늘리는 방향으로 반영한다 — 복무 기간만큼 나이가 들어도
     * 인정한다는 뜻이라 {@link #ageWithinYearsAdjusted}의 병역 보정과 같은 방향이다.
     */
    private ConditionResult ageRangeAdjusted(RuleCondition condition, PlanInput input) {
        LocalDate birthDate = input == null ? null : input.getBirthDate();
        if (birthDate == null) {
            return needInfo(condition, null);
        }
        Optional<Fact> minFact = resolveFact(condition.factCode());
        Optional<Fact> maxFact = resolveFact(condition.altFactCode());
        if (minFact.isEmpty() || maxFact.isEmpty()) {
            return needInfo(condition, null);
        }
        int minAge = minFact.get().requireNumber().intValueExact();
        int maxAge = maxFact.get().requireNumber().intValueExact();
        long adjustMonths = "military_months".equals(condition.adjustField()) && input.getMilitaryMonths() != null
                ? Math.max(0, input.getMilitaryMonths())
                : 0L;
        LocalDate today = LocalDate.now(clock);
        LocalDate minDate = birthDate.plusYears(minAge);
        LocalDate maxDate = birthDate.plusYears(maxAge + 1L).plusMonths(adjustMonths);
        boolean tooYoung = today.isBefore(minDate);
        boolean pass = !tooYoung && today.isBefore(maxDate);
        // 너무 어리면 하한 fact를, 그 외(충족·상한 초과)엔 상한 fact를 근거로 남긴다.
        return met(condition, pass, tooYoung ? minFact.get() : maxFact.get());
    }

    /**
     * FCT-086 분해(POL-02-02, #116). 급여소득자(factCode)/종합소득자(altFactCode) 기준이
     * 다르다. 소상공인 매출 기준(FCT-183)은 매출 데이터를 안 걷어서 이 op가 못 다룬다 —
     * UNEMPLOYED거나 고용형태를 모르면 NEED_INFO.
     */
    private ConditionResult annualLteByEmploymentType(RuleCondition condition, PlanInput input) {
        if (input == null) {
            return needInfo(condition, null);
        }
        EmploymentType employmentType = input.getEmploymentType();
        if (employmentType == null || employmentType == EmploymentType.UNEMPLOYED) {
            return needInfo(condition, "고용형태를 확인해야 소득 기준을 알 수 있습니다.");
        }
        Long monthly = input.getMonthlyIncome();
        if (monthly == null) {
            return needInfo(condition, null);
        }
        boolean isSalaried = employmentType == EmploymentType.FULL_TIME
                || employmentType == EmploymentType.CONTRACT
                || employmentType == EmploymentType.INTERN
                || employmentType == EmploymentType.DAILY_WORKER;
        String factCode = isSalaried ? condition.factCode() : condition.altFactCode();
        Optional<Fact> fact = resolveFact(factCode);
        if (fact.isEmpty()) {
            return needInfo(condition, null);
        }
        long annual = Math.multiplyExact(monthly, 12L);
        boolean pass = BigDecimal.valueOf(annual).compareTo(fact.get().requireNumber()) <= 0;
        return met(condition, pass, fact.get());
    }

    /**
     * FCT-054 (공시가 126% 룰: 전세보증금 ≤ 공시가격 × 1.26). plan_input 이 아니라 매물의
     * {@code deposit}/{@code official_price} 를 쓴다 — 계약 전 특정 매물을 검증하는 조건이라
     * {@code field} 값과 무관하게 이 op 자체가 무엇을 비교할지 고정돼 있다.
     */
    private ConditionResult depositWithinPriceRatio(RuleCondition condition, Property property) {
        if (property == null || property.getDeposit() == null || property.getOfficialPrice() == null) {
            return needInfo(condition, null);
        }
        Optional<Fact> fact = resolveFact(condition.factCode());
        if (fact.isEmpty()) {
            return needInfo(condition, null);
        }
        BigDecimal threshold = BigDecimal.valueOf(property.getOfficialPrice())
                .multiply(fact.get().requireNumber());
        boolean pass = BigDecimal.valueOf(property.getDeposit()).compareTo(threshold) <= 0;
        return met(condition, pass, fact.get());
    }

    /** 판정을 막지 않는 참고용 조건. 원문 안내만 하고 fact 가 있으면 항상 충족으로 둔다
     * (반환보증 LIMIT 조건 등, #126) — fact 자체가 없으면 그때만 확인 필요로 남긴다. */
    private ConditionResult referenceOnly(RuleCondition condition) {
        Optional<Fact> fact = resolveFact(condition.factCode());
        if (fact.isEmpty()) {
            return needInfo(condition, null);
        }
        return met(condition, true, fact.get());
    }

    private Object resolveField(String field, PlanInput input) {
        return resolveField(field, input, null);
    }

    /** 매물 조건(위반건축물·다가구 등, #100)과 plan_input 조건을 이름 하나로 같이 찾는다. */
    private Object resolveField(String field, PlanInput input, Property property) {
        if (field == null) {
            return null;
        }
        return switch (field) {
            case "household_homeless" -> input == null ? null : input.getHouseholdHomeless();
            case "householder_status" -> input == null ? null : input.getHouseholderStatus();
            case "has_existing_jeonse_loan" -> input == null ? null : input.getExistingJeonseLoan();
            case "monthly_income" -> input == null ? null : input.getMonthlyIncome();
            case "net_assets" -> input == null ? null : input.getNetAssets();
            case "hope_deposit" -> input == null ? null : input.getHopeDeposit();
            case "area_m2" -> input == null ? null : input.getAreaM2();
            case "is_violation_building" -> property == null ? null : property.getViolationBuilding();
            case "is_multi_household" -> property == null ? null : property.getMultiHousehold();
            default -> null;
        };
    }

    private Optional<Fact> resolveFact(String factCode) {
        if (factCode == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(facts.require(factCode));
        } catch (FactNotFoundException | UnusableFactException e) {
            return Optional.empty();
        }
    }

    private ConditionResult met(RuleCondition condition, boolean isMet, Fact fact) {
        return met(condition, isMet, fact, null);
    }

    /** factCode 는 조건식이 아니라 실제로 쓴 fact(fact.code())를 기준으로 남긴다 — 대부분의
     * op 는 둘이 같지만, annual_lte_by_age_group 처럼 갈래에 따라 factCode/altFactCode 중
     * 하나를 골라 쓰는 op 는 다르다. 틀린 factCode 가 verdict_basis 에 남으면 안 된다.
     *
     * <p>eligibleUntil 은 연령 상한처럼 시간이 지나면 사라지는 조건에서만 채운다(POL-01-04). */
    private ConditionResult met(RuleCondition condition, boolean isMet, Fact fact, LocalDate eligibleUntil) {
        String label = fact != null ? fact.item() : STATIC_LABELS.getOrDefault(condition.code(), condition.code());
        String requiredText = fact != null ? fact.text() : null;
        String sourceUrl = fact != null ? fact.sourceUrl() : null;
        String factCode = fact != null ? fact.code() : condition.factCode();
        return new ConditionResult(condition.code(), label, requiredText, isMet, factCode, sourceUrl, eligibleUntil);
    }

    private ConditionResult needInfo(RuleCondition condition, String requiredTextOverride) {
        Optional<Fact> fact = resolveFact(condition.factCode());
        String label =
                fact.map(Fact::item).orElseGet(() -> STATIC_LABELS.getOrDefault(condition.code(), condition.code()));
        String requiredText = requiredTextOverride != null
                ? requiredTextOverride
                : fact.map(Fact::text).orElse(null);
        String sourceUrl = fact.map(Fact::sourceUrl).orElse(null);
        return new ConditionResult(condition.code(), label, requiredText, null, condition.factCode(), sourceUrl);
    }
}
