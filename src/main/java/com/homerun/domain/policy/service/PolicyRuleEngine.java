package com.homerun.domain.policy.service;

import com.homerun.domain.fact.exception.FactNotFoundException;
import com.homerun.domain.fact.exception.UnusableFactException;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.plan.type.EmploymentType;
import com.homerun.domain.plan.type.FinancialValueSource;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.domain.policy.model.AgeEligibilityGap;
import com.homerun.domain.policy.model.ConditionResult;
import com.homerun.domain.policy.model.ExpectedEstimate;
import com.homerun.domain.policy.model.RateSpec;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(PolicyRuleEngine.class);

    /** factCode 가 없는 조건(무주택·세대주 등)의 화면 표시 문구. rule_json 에 사람이 읽을 라벨이
     * 없어서 여기 최소한으로 둔다 — 조건이 늘어나면 이 표도 늘어난다. */
    private static final Map<String, String> STATIC_LABELS = Map.of(
            "HOUSEHOLD_HOMELESS", "세대원 전원 무주택",
            "HOUSEHOLDER_STATUS", "세대주 또는 예비 세대주",
            "NO_DUPLICATE_LOAN", "기금·전세·주택담보 중복대출 금지",
            "NOT_VIOLATION_BUILDING", "위반건축물이 아님",
            "NOT_MULTI_HOUSEHOLD", "다가구 주택이 아님",
            "RESIDENTIAL_USE", "주거용 주택(근린생활시설 아님)",
            "REGION_TARGET", "희망 지역이 서울",
            "HOUSE_TYPE", "대상 주택 유형");

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
        RateBounds rate = resolveRate(document.rate(), input);
        BigDecimal rateMin = rate.min();
        BigDecimal rateMax = rate.max();

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

    /** 계산 모드면 소득구간표로 확정금리를, 아니면 min/max 팩트로 범위를 돌려준다. */
    private RateBounds resolveRate(RateSpec rate, PlanInput input) {
        if (rate == null) {
            return RateBounds.NONE;
        }
        if (rate.isComputed()) {
            BigDecimal computed = computedRate(
                    rate, input.getMonthlyIncome(), regions.resolve(input.getRegionId()), input.getCompanySize());
            return computed == null ? RateBounds.NONE : new RateBounds(computed, computed);
        }
        return rangeRate(rate);
    }

    /** min/max 팩트 범위. 값이 없거나 음수·역전이면 지어내지 않고 NONE. */
    private RateBounds rangeRate(RateSpec rate) {
        BigDecimal min = factNumber(rate.minFactCode());
        BigDecimal max = factNumber(rate.maxFactCode());
        if (min == null || max == null || min.signum() < 0 || min.compareTo(max) > 0) {
            return RateBounds.NONE;
        }
        return new RateBounds(min, max);
    }

    /**
     * 청년 버팀목 확정금리(POL-02, 2-8 확정표) = 소득구간 기본금리 − 지방 조정(비수도권 −0.2%p)
     * − 우대(중소·중견·창업 재직 0.3%p, 합산 상한 0.5%p). 확정 단일 금리다.
     *
     * <p>소득·구간·우대 팩트가 하나라도 없으면 금리를 지어내지 않고 null 이다 — 금액은 estimate
     * 가 따로 계산하므로 금리만 비게 된다(NFR-01-06).
     */
    BigDecimal computedRate(RateSpec rate, Long monthlyIncome, PolicyArea area, CompanySize companySize) {
        if (monthlyIncome == null || monthlyIncome < 0) {
            return null;
        }
        long annualIncome = Math.multiplyExact(monthlyIncome, 12L);
        BigDecimal result = baseRateForIncome(rate.incomeBands(), annualIncome);
        if (result == null) {
            return null;
        }
        if (area == PolicyArea.NON_CAPITAL) {
            BigDecimal discount = factNumber(rate.regionalDiscountFactCode());
            if (discount == null) {
                return null;
            }
            result = result.subtract(discount);
        }
        BigDecimal preference = BigDecimal.ZERO;
        if (companySize != null && companySize.qualifiesForYouthEmploymentRatePreference()) {
            BigDecimal sme = factNumber(rate.smePreferenceFactCode());
            if (sme == null) {
                return null;
            }
            preference = preference.add(sme);
        }
        BigDecimal cap = factNumber(rate.preferenceCapFactCode());
        if (cap != null && preference.compareTo(cap) > 0) {
            preference = cap;
        }
        return result.subtract(preference).max(BigDecimal.ZERO);
    }

    /** 연소득이 이하인 첫 구간의 기본금리. 최고 구간도 초과하면(자격에서 이미 FAIL) 계산 불가라 null. */
    private BigDecimal baseRateForIncome(List<RateSpec.IncomeBand> bands, long annualIncome) {
        if (bands == null) {
            return null;
        }
        for (RateSpec.IncomeBand band : bands) {
            BigDecimal ceiling = factNumber(band.ceilingFactCode());
            BigDecimal bandRate = factNumber(band.rateFactCode());
            if (ceiling == null || bandRate == null) {
                return null;
            }
            if (BigDecimal.valueOf(annualIncome).compareTo(ceiling) <= 0) {
                return bandRate;
            }
        }
        return null;
    }

    private BigDecimal factNumber(String factCode) {
        return resolveFact(factCode).map(Fact::number).orElse(null);
    }

    /** 금리 하한·상한. 계산 모드는 단일 값이라 둘이 같고, 못 구하면 NONE(둘 다 null). */
    private record RateBounds(BigDecimal min, BigDecimal max) {
        static final RateBounds NONE = new RateBounds(null, null);
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
            case "prohibited_loan_check" -> prohibitedLoanCheck(condition, input);
            case "in" -> membershipCheck(condition, input, property);
            case "ne" -> equalityCheck(condition, input, property, false);
            case "lte" -> numericCheck(condition, input, property, false);
            case "lte_by_region" -> regionalLimit(condition, input, property);
            case "region_eq" -> regionEquals(condition, input);
            case "gte" -> numericCheck(condition, input, property, true);
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

    /**
     * 1루의 {@code has_existing_jeonse_loan}만으로는 공식 중복대출 금지 범위를 전부 확인할 수 없다.
     *
     * <p>사용자가 기존 전세대출이 있다고 답한 경우에는 명확히 불충족이다. 없다고 답한 것만으로는
     * 성년 세대원의 기금대출과 차주·배우자의 주택담보대출까지 확인한 것이 아니라, 나머지 범위까지
     * 없음을 사용자가 직접 확인한({@code prohibited_loan_confirmed}) 경우에만 충족으로 본다.
     *
     * <p>확인값은 사용자 진술이지 은행 검증이 아니다 — 확인하지 않으면(null·false) 예전처럼 은행
     * 추가 확인 상태로 남긴다. 안 물어본 것을 통과로 지어내지 않는다(NFR-01-06).
     */
    private ConditionResult prohibitedLoanCheck(RuleCondition condition, PlanInput input) {
        Object fieldValue = resolveField(condition.field(), input);
        if (Boolean.TRUE.equals(fieldValue)) {
            return met(condition, false, resolveFact(condition.factCode()).orElse(null));
        }
        if (input != null && Boolean.TRUE.equals(input.getProhibitedLoanConfirmed())) {
            return met(condition, true, resolveFact(condition.factCode()).orElse(null));
        }
        return needInfo(condition, "세대원 기금대출과 차주·배우자의 전세·주택담보대출을 은행에서 확인해야 합니다.");
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
        // fact_code 가 있으면 근거 문구와 출처를 붙인다(#160). 기존 eq 조건은 전부 fact_code 가
        // 없어 예전처럼 STATIC_LABELS 로 떨어지므로 동작이 그대로다.
        return met(
                condition,
                expectEqual == equal,
                resolveFact(condition.factCode()).orElse(null));
    }

    /**
     * 필드 값이 목록에 있는가(BR-09 주택유형 등). enum 이면 이름으로 비교한다.
     *
     * <p>value 가 목록이 아니면 조건식이 잘못 적힌 것이다 — 모르는 op 와 같은 취급으로
     * NEED_INFO 로 떨어뜨린다. 잘못된 조건식으로 가능·불가를 단정하지 않는다.
     */
    private ConditionResult membershipCheck(RuleCondition condition, PlanInput input, Property property) {
        Object fieldValue = resolveField(condition.field(), input, property);
        if (fieldValue == null) {
            return needInfo(condition, null);
        }
        if (!(condition.value() instanceof List<?> allowed)) {
            return needInfo(condition, "이 조건은 자동판정 대상이 아닙니다. 원문을 직접 확인해야 합니다.");
        }
        String actual = fieldValue instanceof Enum<?> enumValue ? enumValue.name() : String.valueOf(fieldValue);
        boolean member = allowed.stream().map(String::valueOf).anyMatch(actual::equals);
        return met(condition, member, resolveFact(condition.factCode()).orElse(null));
    }

    /** field 가 BIGINT(금액 등)든 NUMERIC(면적 등)이든 상관없이 fact 와 비교한다 — 둘 다
     * plan_input 에 섞여 있어서(#102) 하나로 받는다. */
    private ConditionResult numericCheck(RuleCondition condition, PlanInput input, Property property, boolean gte) {
        if (financialValueNeedsConfirmation(condition.field(), input)) {
            return needInfo(condition, "외부 금융정보를 사용자가 확인해야 합니다.");
        }
        // 매물을 함께 넘긴다 — area_m2 처럼 매물이 있으면 그쪽을 우선하는 필드가 있다(BR-09).
        BigDecimal amount = toComparable(resolveField(condition.field(), input, property));
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

    private ConditionResult regionalLimit(RuleCondition condition, PlanInput input, Property property) {
        PolicyArea area = regions.resolve(input == null ? null : input.getRegionId());
        if (area == null) return needInfo(condition, "희망 지역을 확인해야 상한을 결정할 수 있습니다.");
        String factCode = area == PolicyArea.NON_CAPITAL ? condition.altFactCode() : condition.factCode();
        RuleCondition selected =
                new RuleCondition(condition.code(), condition.field(), "lte", condition.value(), factCode, null);
        return numericCheck(selected, input, property, false);
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
        if (financialValueNeedsConfirmation("monthly_income", input)) {
            return needInfo(condition, "오픈뱅킹 실수령 추정값을 확인해 주세요.");
        }
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
        if (financialValueNeedsConfirmation("monthly_income", input)) {
            return needInfo(condition, "오픈뱅킹 실수령 추정값을 확인해 주세요.");
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
        long adjustMonths = militaryAdjustMonths(condition, input);
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
        long adjustMonths = militaryAdjustMonths(condition, input);
        LocalDate today = LocalDate.now(clock);
        LocalDate minDate = birthDate.plusYears(minAge);
        LocalDate maxDate = birthDate.plusYears(maxAge + 1L).plusMonths(adjustMonths);
        boolean tooYoung = today.isBefore(minDate);
        boolean pass = !tooYoung && today.isBefore(maxDate);
        // 너무 어리면 하한 fact를, 그 외(충족·상한 초과)엔 상한 fact를 근거로 남긴다.
        return met(condition, pass, tooYoung ? minFact.get() : maxFact.get());
    }

    /**
     * 병역 보정 개월수(BR-01). 중소·중견 재직 또는 창업지원 대상자만 인정한다 — 대기업·공공·기타·
     * 미상은 0이다. 모든 병역 이행자에게 상한을 늘려 주면 잘못된 통과가 된다.
     */
    private long militaryAdjustMonths(RuleCondition condition, PlanInput input) {
        if (input == null
                || !"military_months".equals(condition.adjustField())
                || input.getMilitaryMonths() == null
                || input.getCompanySize() == null
                || !input.getCompanySize().qualifiesForMilitaryAgeExtension()) {
            return 0L;
        }
        return Math.max(0, input.getMilitaryMonths());
    }

    /**
     * 나이 하한을 아직 못 채운 조건이면 채우는 예정일을 반환한다(ALT-01-04 재도전 큐 전용).
     * 판정 자체엔 관여하지 않는다 — 생년월일처럼 확정된 값에서 계산 가능한 미래 날짜만 다룬다.
     * 소득·자산처럼 언제 바뀔지 알 수 없는 조건은 여기서 다루지 않는다(근거 없는 예측 금지).
     */
    public Optional<AgeEligibilityGap> upcomingAgeEligibility(RuleCondition condition, PlanInput input) {
        if (!"age_range_adjusted".equals(condition.op())) {
            return Optional.empty();
        }
        LocalDate birthDate = input == null ? null : input.getBirthDate();
        if (birthDate == null) {
            return Optional.empty();
        }
        Optional<Fact> minFact = resolveFact(condition.factCode());
        if (minFact.isEmpty()) {
            return Optional.empty();
        }
        Fact fact = minFact.get();
        LocalDate minDate = birthDate.plusYears(fact.requireNumber().intValueExact());
        if (!LocalDate.now(clock).isBefore(minDate)) {
            return Optional.empty();
        }
        return Optional.of(new AgeEligibilityGap(minDate, fact.item(), fact.sourceUrl()));
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
        if (financialValueNeedsConfirmation("monthly_income", input)) {
            return needInfo(condition, "오픈뱅킹 실수령 추정값을 확인해 주세요.");
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
    /** 매물 면적이 있으면 그것을, 없으면 계획 입력의 희망 면적을 쓴다. 둘 다 없으면 null 이라
     * 면적 조건이 NEED_INFO 로 떨어진다 — 0 으로 채우면 85㎡ 이하로 통과해 버린다. */
    private Object propertyAreaOr(Property property, PlanInput input) {
        if (property != null && property.getExclusiveArea() != null) {
            return property.getExclusiveArea();
        }
        return input == null ? null : input.getAreaM2();
    }

    private Object resolveField(String field, PlanInput input, Property property) {
        if (field == null) {
            return null;
        }
        return switch (field) {
            case "household_homeless" -> input == null ? null : input.getHouseholdHomeless();
            case "marital_status" -> input == null ? null : input.getMaritalStatus();
            case "lives_apart_from_parents" -> input == null ? null : input.getLivesApartFromParents();
            case "parent_on_housing_benefit" -> input == null ? null : input.getParentOnHousingBenefit();
            case "householder_status" -> input == null ? null : input.getHouseholderStatus();
            case "has_existing_jeonse_loan" -> input == null ? null : input.getExistingJeonseLoan();
            case "monthly_income" -> input == null ? null : input.getMonthlyIncome();
            case "net_assets" -> input == null ? null : input.getNetAssets();
            case "hope_deposit" -> input == null ? null : input.getHopeDeposit();
            // 매물이 붙어 있으면 실제 전용면적을 쓴다(BR-09). plan_input 의 면적은 1루에서
            // 받은 희망 조건이라, 매물을 정한 뒤에는 그 집의 면적으로 판정해야 한다.
            // is_violation_building 이 매물에서만 읽는 것과 같은 방향이다.
            case "area_m2" -> propertyAreaOr(property, input);
            // 매물에서만 읽는다. plan_input 의 HouseType 은 값 체계가 다른 enum(VILLA·DETACHED
            // 등)이라 섞으면 화이트리스트와 항상 미일치가 난다.
            case "house_type" -> property == null ? null : property.getHouseType();
            case "is_violation_building" -> property == null ? null : property.getViolationBuilding();
            case "is_multi_household" -> property == null ? null : property.getMultiHousehold();
            // 근린생활시설(비주거)은 모든 전세 상품이 불가다(BR-09). 위 둘과 같이 매물에서만 읽는다.
            case "is_non_residential" -> property == null ? null : property.getNonResidential();
            default -> unknownField(field);
        };
    }

    /**
     * 조건식에는 있는데 여기엔 없는 필드. 값을 모르는 것과 필드를 모르는 것은 다르다 — 앞은
     * 정상적인 NEED_INFO 지만, 뒤는 시드(rule_json)와 엔진이 어긋난 채 배포된 사고다.
     * RESIDENTIAL_USE 가 이렇게 조용히 죽어 있었다(#364).
     *
     * <p>판정은 그대로 NEED_INFO 로 안전하게 두되(여기서 기본값을 지어내면 그게 틀린 안내다,
     * NFR-01-06) 로그로 드러낸다. throw 하지 않는 이유는 규칙 한 줄의 오타가 판정 API 전체를
     * 500 으로 만들면 안 되기 때문이다. 아는 필드가 null 인 경우는 여기로 오지 않으므로
     * 정상 동작이 시끄러워지지 않는다.
     */
    private Object unknownField(String field) {
        log.warn(
                "rule_json 에 엔진이 모르는 field 가 있어 조건을 판정하지 못했습니다. field={} "
                        + "— resolveField 에 case 를 추가하거나 조건식을 고쳐야 합니다.",
                field);
        return null;
    }

    private boolean financialValueNeedsConfirmation(String field, PlanInput input) {
        if (input == null || Boolean.TRUE.equals(input.getFinancialDataConfirmed())) return false;
        return ("monthly_income".equals(field) && input.getIncomeSource() == FinancialValueSource.OPEN_BANKING)
                || ("net_assets".equals(field) && input.getAssetSource() == FinancialValueSource.OPEN_BANKING);
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
        return new ConditionResult(
                condition.code(),
                label,
                requiredText,
                isMet,
                factCode,
                sourceUrl,
                eligibleUntil,
                condition.effectiveBasis());
    }

    private ConditionResult needInfo(RuleCondition condition, String requiredTextOverride) {
        Optional<Fact> fact = resolveFact(condition.factCode());
        String label =
                fact.map(Fact::item).orElseGet(() -> STATIC_LABELS.getOrDefault(condition.code(), condition.code()));
        String requiredText = requiredTextOverride != null
                ? requiredTextOverride
                : fact.map(Fact::text).orElse(null);
        String sourceUrl = fact.map(Fact::sourceUrl).orElse(null);
        return new ConditionResult(
                condition.code(),
                label,
                requiredText,
                null,
                condition.factCode(),
                sourceUrl,
                condition.effectiveBasis());
    }
}
