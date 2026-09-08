package com.homerun.domain.property.service.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.type.CheckResult;
import org.junit.jupiter.api.Test;

/** 예산 초과 규칙(F06). 실제 보증금이 1루 희망예산을 넘으면 경고한다. */
class BudgetExceededRuleTest {

    private final BudgetExceededRule rule = new BudgetExceededRule();

    @Test
    void should_warn_when_depositExceedsBudget() {
        var finding = rule.evaluate(facts(120_000_000L, 100_000_000L)).orElseThrow();

        assertThat(finding.result()).isEqualTo(CheckResult.WARN);
        assertThat(finding.checkCode()).isEqualTo("BUDGET_EXCEEDED");
        assertThat(finding.summary()).contains("초과");
        assertThat(finding.action()).isNotBlank();
    }

    @Test
    void should_pass_when_depositWithinBudget() {
        var finding = rule.evaluate(facts(90_000_000L, 100_000_000L)).orElseThrow();

        assertThat(finding.result()).isEqualTo(CheckResult.PASS);
        assertThat(finding.checkCode()).isEqualTo("BUDGET_EXCEEDED");
    }

    @Test
    void should_pass_when_depositEqualsBudget() {
        assertThat(rule.evaluate(facts(100_000_000L, 100_000_000L))
                        .orElseThrow()
                        .result())
                .isEqualTo(CheckResult.PASS);
    }

    @Test
    void should_notApply_when_budgetUnknown() {
        // 희망예산을 모르면 한도를 모르는 것이라 초과라고 단정하지 않는다.
        assertThat(rule.appliesTo(facts(120_000_000L, null))).isFalse();
    }

    @Test
    void should_notApply_when_pureMonthlyRentWithNoDeposit() {
        assertThat(rule.appliesTo(facts(0L, 100_000_000L))).isFalse();
    }

    private PropertyFacts facts(long deposit, Long hopeDeposit) {
        return new PropertyFacts(
                LeaseType.JEONSE,
                deposit,
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
                false,
                hopeDeposit);
    }
}
