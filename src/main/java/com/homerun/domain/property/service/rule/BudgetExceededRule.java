package com.homerun.domain.property.service.rule;

import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.CheckFinding;
import com.homerun.domain.property.service.PropertyRiskRule;
import com.homerun.domain.property.type.CheckResult;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 예산 초과 판정(F06).
 *
 * <p>실제 매물 보증금({@link PropertyFacts#deposit()})이 1루에서 정한 희망예산
 * ({@link PropertyFacts#hopeDeposit()})을 넘는지 본다. 두 값은 별개다 — 희망예산은 사용자가
 * 감당하기로 한 한도이고, 보증금은 이 집이 실제로 요구하는 금액이다. 넘으면 경고로 남겨
 * 예산을 초과하는 매물을 걸러낼 수 있게 한다.
 *
 * <p>희망예산을 확인하지 못했으면(null) 판정하지 않는다 — 한도를 모르면서 초과라고 단정하지
 * 않는다.
 */
@Component
class BudgetExceededRule implements PropertyRiskRule {

    private static final String CHECK_CODE = "BUDGET_EXCEEDED";
    private static final String CHECK_LABEL = "희망예산 대비 보증금";

    @Override
    public boolean appliesTo(PropertyFacts facts) {
        return facts.deposit() > 0 && facts.hopeDeposit() != null && facts.hopeDeposit() > 0;
    }

    @Override
    public Optional<CheckFinding> evaluate(PropertyFacts facts) {
        long budget = facts.hopeDeposit();
        long deposit = facts.deposit();
        if (deposit > budget) {
            return Optional.of(finding(
                    CheckResult.WARN,
                    "보증금 %,d원이 희망예산 %,d원을 %,d원 초과한다.".formatted(deposit, budget, deposit - budget),
                    "예산을 다시 확인하거나 보증금이 낮은 매물을 찾는다."));
        }
        return Optional.of(finding(CheckResult.PASS, "보증금 %,d원이 희망예산 %,d원 이내다.".formatted(deposit, budget), null));
    }

    private CheckFinding finding(CheckResult result, String summary, String action) {
        return new CheckFinding(CHECK_CODE, CHECK_LABEL, result, summary, action, null, null, false);
    }
}
