package com.homerun.domain.policy.service;

import com.homerun.domain.fact.exception.FactNotFoundException;
import com.homerun.domain.fact.exception.UnusableFactException;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.policy.model.ConditionResult;
import com.homerun.domain.policy.model.RuleCondition;
import com.homerun.domain.policy.model.RuleDocument;
import com.homerun.domain.property.entity.Property;
import java.math.BigDecimal;
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
            "NO_DUPLICATE_LOAN", "기존 전세자금대출 없음");

    private final FactRegistry facts;
    private final Clock clock;

    public PolicyRuleEngine(FactRegistry facts, Clock clock) {
        this.facts = facts;
        this.clock = clock;
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

    private ConditionResult evaluateOne(RuleCondition condition, PlanInput input, Property property) {
        return switch (condition.op()) {
            case "eq" -> equalityCheck(condition, input, true);
            case "ne" -> equalityCheck(condition, input, false);
            case "lte" -> numericCheck(condition, input, false);
            case "gte" -> numericCheck(condition, input, true);
            case "annual_lte" -> annualIncomeCheck(condition, input);
            case "age_within_years_adjusted" -> ageWithinYearsAdjusted(condition, input);
            case "deposit_lte_price_times_fact" -> depositWithinPriceRatio(condition, property);
            default -> needInfo(condition, "이 조건은 자동판정 대상이 아닙니다. 원문을 직접 확인해야 합니다.");
        };
    }

    private ConditionResult equalityCheck(RuleCondition condition, PlanInput input, boolean expectEqual) {
        Object fieldValue = resolveField(condition.field(), input);
        if (fieldValue == null) {
            return needInfo(condition, null);
        }
        String actual = fieldValue instanceof Enum<?> enumValue ? enumValue.name() : String.valueOf(fieldValue);
        String expected = String.valueOf(condition.value());
        boolean equal = actual.equals(expected);
        return met(condition, expectEqual == equal, null);
    }

    private ConditionResult numericCheck(RuleCondition condition, PlanInput input, boolean gte) {
        Object fieldValue = resolveField(condition.field(), input);
        if (fieldValue == null || !(fieldValue instanceof Long amount)) {
            return needInfo(condition, null);
        }
        Optional<Fact> fact = resolveFact(condition.factCode());
        if (fact.isEmpty()) {
            return needInfo(condition, null);
        }
        int cmp = BigDecimal.valueOf(amount).compareTo(fact.get().requireNumber());
        boolean pass = gte ? cmp >= 0 : cmp <= 0;
        return met(condition, pass, fact.get());
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
     * FCT-002 (병역 보정: 만 34세 종료일 + 복무기간, 최대 만 39세) 를 그대로 옮긴다. 종료
     * 상한을 만 40세 생일 전까지로 못박아서, 복무기간이 길어도 그 이상은 인정하지 않는다.
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

    private Object resolveField(String field, PlanInput input) {
        if (field == null || input == null) {
            return null;
        }
        return switch (field) {
            case "household_homeless" -> input.getHouseholdHomeless();
            case "householder_status" -> input.getHouseholderStatus();
            case "has_existing_jeonse_loan" -> input.getExistingJeonseLoan();
            case "monthly_income" -> input.getMonthlyIncome();
            case "net_assets" -> input.getNetAssets();
            case "hope_deposit" -> input.getHopeDeposit();
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
        String label = fact != null ? fact.item() : STATIC_LABELS.getOrDefault(condition.code(), condition.code());
        String requiredText = fact != null ? fact.text() : null;
        String sourceUrl = fact != null ? fact.sourceUrl() : null;
        return new ConditionResult(condition.code(), label, requiredText, isMet, condition.factCode(), sourceUrl);
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
