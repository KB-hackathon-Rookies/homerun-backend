package com.homerun.domain.property.service.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.type.CheckResult;
import org.junit.jupiter.api.Test;

/** 근린생활시설 규칙(BR-09). 근생은 모든 전세 상품이 불가다(FCT-120). */
class NonResidentialRuleTest {

    private final NonResidentialRule rule = new NonResidentialRule();

    @Test
    void should_block_when_buildingIsNonResidential() {
        var finding = rule.evaluate(facts(true)).orElseThrow();

        assertThat(finding.result()).isEqualTo(CheckResult.BLOCK);
        assertThat(finding.checkCode()).isEqualTo("NON_RESIDENTIAL");
        assertThat(finding.factCode()).isEqualTo("FCT-120");
    }

    @Test
    void should_pass_when_buildingIsResidential() {
        assertThat(rule.evaluate(facts(false)).orElseThrow().result()).isEqualTo(CheckResult.PASS);
    }

    @Test
    void should_requireCheck_when_mainPurposeIsUnknown() {
        // 모르는 것을 주거용으로 단정하지 않는다 — 근생은 겉보기에 빌라와 똑같다.
        assertThat(rule.evaluate(facts(null)).orElseThrow().result()).isEqualTo(CheckResult.UNKNOWN);
    }

    private PropertyFacts facts(Boolean nonResidential) {
        return new PropertyFacts(
                LeaseType.JEONSE,
                100_000_000L,
                "11680",
                null,
                null,
                0L,
                true,
                false,
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                nonResidential);
    }
}
