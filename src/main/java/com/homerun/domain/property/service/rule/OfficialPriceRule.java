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
 * <p>두 조건을 모두 넘어야 가입할 수 있다.
 *
 * <ul>
 *   <li>공시가 126% 룰(FCT-054) — 보증금이 공시가격의 1.26배 이하
 *   <li>담보인정비율(FCT-055) — <b>보증금 + 선순위채권</b>이 주택가격의 90% 이내
 * </ul>
 *
 * <p>보증금만 보면 근저당이 잔뜩 잡힌 매물도 통과한다. 선순위채권이 있으면 경매에서 그쪽이
 * 먼저 가져가므로 보증기관이 받아주지 않는다.
 *
 * <p>대출 심사와 보증 심사는 별개다. 대출이 나와도 여기서 막히면 보증금을 지킬 수단이 없다.
 */
@Component
class OfficialPriceRule implements PropertyRiskRule {

    private static final String PRICE_FACT = "FCT-054";
    private static final String LTV_FACT = "FCT-055";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

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
                    CheckResult.UNKNOWN,
                    "공시가격을 확인하지 못해 반환보증 가입 가능 여부를 판단할 수 없다.",
                    "부동산공시가격 알리미에서 공시가격을 확인한다.",
                    PRICE_FACT));
        }
        if (propertyFacts.seniorDebt() == null) {
            return Optional.of(finding(
                    CheckResult.UNKNOWN,
                    "선순위채권을 확인하지 못해 담보인정비율을 계산할 수 없다.",
                    "등기사항전부증명서 을구에서 근저당 채권최고액을 확인한다.",
                    LTV_FACT));
        }

        long priceCeiling =
                ceiling(propertyFacts.officialPrice(), facts.require(PRICE_FACT).requireNumber());
        if (propertyFacts.deposit() > priceCeiling) {
            return Optional.of(finding(
                    CheckResult.BLOCK,
                    "보증금 %,d원이 공시가격 기준 상한 %,d원을 넘어 반환보증에 가입할 수 없다.".formatted(propertyFacts.deposit(), priceCeiling),
                    "보증금을 %,d원 이하로 낮추거나 다른 매물을 확인한다.".formatted(priceCeiling),
                    PRICE_FACT));
        }

        // 담보인정비율은 주택가격 기준이다. 시세를 모르면 공시가격으로 대신 본다.
        long housePrice = propertyFacts.marketPrice() != null && propertyFacts.marketPrice() > 0
                ? propertyFacts.marketPrice()
                : propertyFacts.officialPrice();
        BigDecimal ltvPercent = facts.require(LTV_FACT).requireNumber();
        long ltvCeiling = ceiling(housePrice, ltvPercent.divide(HUNDRED, 10, RoundingMode.HALF_UP));
        long secured = propertyFacts.deposit() + propertyFacts.seniorDebt();

        if (secured > ltvCeiling) {
            return Optional.of(finding(
                    CheckResult.BLOCK,
                    "보증금과 선순위채권 합계 %,d원이 담보인정 한도 %,d원을 넘어 반환보증에 가입할 수 없다.".formatted(secured, ltvCeiling),
                    "선순위채권 상환을 조건으로 특약을 넣거나 다른 매물을 확인한다.",
                    LTV_FACT));
        }
        return Optional.of(finding(
                CheckResult.PASS,
                "보증금 %,d원과 선순위채권 %,d원이 두 기준을 모두 넘지 않는다.".formatted(propertyFacts.deposit(), propertyFacts.seniorDebt()),
                null,
                PRICE_FACT));
    }

    private long ceiling(long base, BigDecimal multiplier) {
        return BigDecimal.valueOf(base)
                .multiply(multiplier)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }

    private CheckFinding finding(CheckResult result, String summary, String action, String factCode) {
        return new CheckFinding(
                "OFFICIAL_PRICE_126",
                "반환보증 가입 가능성",
                result,
                summary,
                action,
                factCode,
                "https://www.khug.or.kr",
                false);
    }
}
