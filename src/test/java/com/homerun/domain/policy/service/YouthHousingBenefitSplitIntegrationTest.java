package com.homerun.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.policy.entity.PolicyRule;
import com.homerun.domain.policy.model.ConditionResult;
import com.homerun.domain.policy.model.RuleCondition;
import com.homerun.domain.policy.repository.PolicyRuleRepository;
import com.homerun.domain.policy.type.HouseholdBasis;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * V36 이 심은 청년 주거급여 분리지급 조건식을 실제 Postgres 에서 확인한다(POL-01-03 Phase 1).
 *
 * <p>연령 경계를 특히 촘촘히 본다. 원문은 "만 30세 미만"인데 판정 엔진의 상한은 "이하" 규약이라
 * 상한 팩트를 29 로 옮겼다 — 30 을 넣었다면 만 30세까지 통과해 1년이 통째로 어긋난다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class YouthHousingBenefitSplitIntegrationTest {

    private static final String POLICY_CODE = "HOUSING-BENEFIT-YOUTH-SPLIT";

    private final PolicyRuleEngine engine;
    private final PolicyRuleRepository rules;
    private final PlanInputRepository inputs;
    private final EntityManager em;

    YouthHousingBenefitSplitIntegrationTest(
            @Autowired PolicyRuleEngine engine,
            @Autowired PolicyRuleRepository rules,
            @Autowired PlanInputRepository inputs,
            @Autowired EntityManager em) {
        this.engine = engine;
        this.rules = rules;
        this.inputs = inputs;
        this.em = em;
    }

    @Test
    void should_seedRuleAsDraft_becauseHumansConfirmConditionsNotTheModel() {
        // ADM-04. 조건식 초안까지가 자동화의 몫이고 ACTIVE 승격은 사람이 한다.
        String status = (String)
                em.createNativeQuery("""
                        SELECT status FROM policy_rule
                        WHERE policy_id = (SELECT id FROM policy WHERE code = :code)
                        """).setParameter("code", POLICY_CODE).getSingleResult();

        assertThat(status).isEqualTo("DRAFT");
    }

    @Test
    void should_markParentConditionsAsOriginHousehold() {
        Map<String, RuleCondition> byCode = seededConditions();

        assertThat(byCode.get("PARENT_ON_HOUSING_BENEFIT").effectiveBasis()).isEqualTo(HouseholdBasis.ORIGIN);
        assertThat(byCode.get("LIVES_APART_FROM_PARENTS").effectiveBasis()).isEqualTo(HouseholdBasis.ORIGIN);
        assertThat(byCode.get("ORIGIN_HOUSEHOLD_INCOME").effectiveBasis()).isEqualTo(HouseholdBasis.ORIGIN);
        // 나이·혼인은 본인에 관한 조건이라 독립가구다.
        assertThat(byCode.get("AGE_RANGE_YOUTH_SPLIT").effectiveBasis()).isEqualTo(HouseholdBasis.SELF);
        assertThat(byCode.get("UNMARRIED").effectiveBasis()).isEqualTo(HouseholdBasis.SELF);
    }

    @Test
    void should_failWithParentReason_when_parentHouseholdIsNotOnHousingBenefit() {
        // 금액을 하나도 안 걷고 확정적인 불가가 나오는 것이 Phase 1 의 요점이다.
        ConditionResult result = evaluateOnly("PARENT_ON_HOUSING_BENEFIT", input(LocalDate.of(2000, 1, 1), false));

        assertThat(result.isMet()).isFalse();
        assertThat(result.householdBasis()).isEqualTo(HouseholdBasis.ORIGIN);
        assertThat(result.requiredText()).contains("부모 가구가 이미 주거급여 수급 중");
    }

    @Test
    void should_needInfo_when_parentHousingBenefitIsUnknown() {
        // 모름은 불가가 아니라 추가확인이다(COM-05-04).
        ConditionResult result = evaluateOnly("PARENT_ON_HOUSING_BENEFIT", input(LocalDate.of(2000, 1, 1), null));

        assertThat(result.isMet()).isNull();
    }

    @Test
    void should_excludeAgeThirty_becauseSourceSaysUnderThirtyNotThirtyOrLess() {
        // 오늘 기준 만 30세 생일 당일. 상한 팩트를 30 으로 넣었다면 여기서 통과해 버린다.
        LocalDate exactlyThirty = LocalDate.now().minusYears(30);

        assertThat(evaluateOnly("AGE_RANGE_YOUTH_SPLIT", input(exactlyThirty, true))
                        .isMet())
                .isFalse();
    }

    @Test
    void should_includeAgeTwentyNine_upToTheDayBeforeThirtieth() {
        LocalDate dayBeforeThirtieth = LocalDate.now().minusYears(30).plusDays(1);

        assertThat(evaluateOnly("AGE_RANGE_YOUTH_SPLIT", input(dayBeforeThirtieth, true))
                        .isMet())
                .isTrue();
    }

    @Test
    void should_includeAgeNineteen_onTheBirthdayItself() {
        LocalDate exactlyNineteen = LocalDate.now().minusYears(19);

        assertThat(evaluateOnly("AGE_RANGE_YOUTH_SPLIT", input(exactlyNineteen, true))
                        .isMet())
                .isTrue();
    }

    @Test
    void should_excludeAgeEighteen_theDayBeforeNineteenth() {
        LocalDate dayBeforeNineteenth = LocalDate.now().minusYears(19).plusDays(1);

        assertThat(evaluateOnly("AGE_RANGE_YOUTH_SPLIT", input(dayBeforeNineteenth, true))
                        .isMet())
                .isFalse();
    }

    @Test
    void should_keepOriginIncomeOnNeedInfo_becauseParentIncomeIsNotCollectedYet() {
        // Phase 2 전까지는 원가구 소득을 걷는 경로가 없다. 지어내지 않고 추가확인으로 둔다.
        ConditionResult result = evaluateOnly("ORIGIN_HOUSEHOLD_INCOME", input(LocalDate.of(2000, 1, 1), true));

        assertThat(result.isMet()).isNull();
        assertThat(result.householdBasis()).isEqualTo(HouseholdBasis.ORIGIN);
    }

    private Map<String, RuleCondition> seededConditions() {
        Long ruleId = ((Number) em.createNativeQuery("""
                        SELECT id FROM policy_rule
                        WHERE policy_id = (SELECT id FROM policy WHERE code = :code)
                        """)
                        .setParameter("code", POLICY_CODE)
                        .getSingleResult())
                .longValue();
        PolicyRule rule = rules.findById(ruleId).orElseThrow();
        return rule.getRuleJson().conditions().stream()
                .collect(java.util.stream.Collectors.toMap(RuleCondition::code, Function.identity()));
    }

    /** 조건식 전체를 돌리고 원하는 조건 하나만 꺼낸다 — 조건마다 필요한 입력이 달라서다. */
    private ConditionResult evaluateOnly(String conditionCode, PlanInput planInput) {
        Long ruleId = ((Number) em.createNativeQuery("""
                        SELECT id FROM policy_rule
                        WHERE policy_id = (SELECT id FROM policy WHERE code = :code)
                        """)
                        .setParameter("code", POLICY_CODE)
                        .getSingleResult())
                .longValue();
        List<ConditionResult> results =
                engine.evaluate(rules.findById(ruleId).orElseThrow().getRuleJson(), planInput);
        return results.stream()
                .filter(result -> result.code().equals(conditionCode))
                .findFirst()
                .orElseThrow();
    }

    /** plan_input 을 직접 넣고 다시 읽는다 — 새 컬럼이 실제로 왕복하는지까지 본다. */
    private PlanInput input(LocalDate birthDate, Boolean parentOnHousingBenefit) {
        Long memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, name)
                        VALUES ('KAKAO', 'split-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        Long planId = ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'WOLSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
        em.createNativeQuery("""
                        INSERT INTO plan_input
                            (plan_id, birth_date, marital_status, lives_apart_from_parents,
                             parent_on_housing_benefit, financial_data_confirmed)
                        VALUES (:pid, :birth, 'SINGLE', true, :parent, true)
                        """)
                .setParameter("pid", planId)
                .setParameter("birth", birthDate)
                .setParameter("parent", parentOnHousingBenefit)
                .executeUpdate();
        em.flush();
        em.clear();
        return inputs.findByPlanId(planId).orElseThrow();
    }
}
