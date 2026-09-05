package com.homerun.domain.policy.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homerun.domain.policy.type.HouseholdBasis;
import org.junit.jupiter.api.Test;

/** rule_json 의 household_basis 를 읽는 규칙(POL-01-03 Phase 0). 기존 조건식을 하나도 안 고쳐도
 * 동작이 같아야 한다는 것이 핵심이다. */
class RuleConditionBasisTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void should_defaultToSelf_when_ruleJsonHasNoHouseholdBasis() throws Exception {
        // 기존 rule_json 은 전부 이 모양이다. 여기서 SELF 가 안 나오면 시드 전체가 바뀐 셈이 된다.
        String json = """
                {"code":"AGE_UPPER_BOUND","field":"birth_date","op":"age_within_years_adjusted","fact_code":"FCT-174"}
                """;

        RuleCondition condition = mapper.readValue(json, RuleCondition.class);

        assertThat(condition.householdBasis()).isNull();
        assertThat(condition.effectiveBasis()).isEqualTo(HouseholdBasis.SELF);
    }

    @Test
    void should_readOrigin_when_ruleJsonDeclaresIt() throws Exception {
        String json = """
                {"code":"HOUSEHOLD_INCOME_RATIO","fact_code":"FCT-087","op":"external_check","household_basis":"ORIGIN"}
                """;

        RuleCondition condition = mapper.readValue(json, RuleCondition.class);

        assertThat(condition.effectiveBasis()).isEqualTo(HouseholdBasis.ORIGIN);
    }

    @Test
    void should_keepNullAndSelfDistinguishable_soSeedsCanBeAudited() {
        // "안 적혀 있음"과 "SELF 라고 적음"은 판정에서 같은 뜻이지만, 시드를 훑어 무엇이 아직
        // 축을 안 밝혔는지 세려면 원본은 구분돼 있어야 한다.
        RuleCondition declared = new RuleCondition("A", "f", "eq", true, null, null, null, HouseholdBasis.SELF);
        RuleCondition undeclared = new RuleCondition("A", "f", "eq", true, null, null);

        assertThat(declared.householdBasis()).isEqualTo(HouseholdBasis.SELF);
        assertThat(undeclared.householdBasis()).isNull();
        assertThat(declared.effectiveBasis()).isEqualTo(undeclared.effectiveBasis());
    }
}
