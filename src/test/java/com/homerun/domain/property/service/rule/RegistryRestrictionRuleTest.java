package com.homerun.domain.property.service.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.type.CheckResult;
import org.junit.jupiter.api.Test;

class RegistryRestrictionRuleTest {

    private final RegistryRestrictionRule rule = new RegistryRestrictionRule();

    @Test
    void should_block_when_leaseholdRegistrationExists() {
        var finding = rule.evaluate(facts(true, false, false)).orElseThrow();

        assertThat(finding.result()).isEqualTo(CheckResult.BLOCK);
        assertThat(finding.checkCode()).isEqualTo("REGISTRY_RESTRICTION");
    }

    @Test
    void should_requireCheck_when_anyRegistryValueIsUnknown() {
        assertThat(rule.evaluate(facts(null, false, false)).orElseThrow().result())
                .isEqualTo(CheckResult.UNKNOWN);
    }

    private PropertyFacts facts(Boolean leasehold, Boolean restriction, Boolean auction) {
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
                leasehold,
                restriction,
                auction,
                null);
    }
}
