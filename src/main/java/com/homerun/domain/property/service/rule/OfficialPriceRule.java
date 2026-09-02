package com.homerun.domain.property.service.rule;

import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.CheckFinding;
import com.homerun.domain.property.service.PropertyRiskRule;
import com.homerun.domain.property.type.CheckResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * PRP-01-03 공시가격 확인 — 반환보증 가입 가능성.
 *
 * <p>보증금이 공시가격의 126%를 넘으면 HUG 반환보증에 가입할 수 없다(FCT-054).
 * 1.26 = 공시가격 140% × 전세가율 90%.
 *
 * <p>대출 심사와 보증 심사는 별개다. 대출이 나와도 여기서 막히면 보증금을 지킬 수단이 없다.
 */
@Component
class OfficialPriceRule implements PropertyRiskRule {

    private static final String FACT = "FCT-054";

    private final FactRegistry facts;

    OfficialPriceRule(FactRegistry facts) {
        this.facts = facts;
    }

    @Override
    public boolean appliesTo(PropertyFacts propertyFacts) {
        // 보증금이 커야 반환보증 실익이 있다. 순수 월세는 소액임차인 규칙이 맡는다.
        return propertyFacts.leaseType() != LeaseType.WOLSE && propertyFacts.deposit() > 0;
    }

    @Override
    public Optional<CheckFinding> evaluate(PropertyFacts propertyFacts) {
        if (propertyFacts.officialPrice() == null || propertyFacts.officialPrice() <= 0) {
            return Optional.of(finding(
                    CheckResult.UNKNOWN, "공시가격을 확인하지 못해 반환보증 가입 가능 여부를 판단할 수 없다.", "부동산공시가격 알리미에서 공시가격을 확인한다."));
        }

        BigDecimal multiplier = facts.require(FACT).requireNumber();
        long ceiling = BigDecimal.valueOf(propertyFacts.officialPrice())
                .multiply(multiplier)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();

        if (propertyFacts.deposit() <= ceiling) {
            return Optional.of(finding(
                    CheckResult.PASS,
                    "보증금 %,d원이 반환보증 가입 상한 %,d원 이하다.".formatted(propertyFacts.deposit(), ceiling),
                    null));
        }
        return Optional.of(finding(
                CheckResult.BLOCK,
                "보증금 %,d원이 반환보증 가입 상한 %,d원을 넘어 가입할 수 없다.".formatted(propertyFacts.deposit(), ceiling),
                "보증금을 %,d원 이하로 낮추거나 다른 매물을 확인한다. 반환보증 없이 계약하면 보증금을 지킬 수단이 없다.".formatted(ceiling)));
    }

    private CheckFinding finding(CheckResult result, String summary, String action) {
        return new CheckFinding(
                "OFFICIAL_PRICE_126",
                "반환보증 가입 가능성",
                result,
                summary,
                action,
                FACT,
                "https://www.realtyprice.kr",
                false);
    }
}
