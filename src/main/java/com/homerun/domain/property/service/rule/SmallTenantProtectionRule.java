package com.homerun.domain.property.service.rule;

import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.CheckFinding;
import com.homerun.domain.property.service.PropertyRiskRule;
import com.homerun.domain.property.type.CheckResult;
import com.homerun.domain.property.type.SmallTenantTier;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * PRP-01-07 소액임차인 보호 판단.
 *
 * <p>보증금이 지역별 상한 이하면 경매에서 일정액을 최우선으로 변제받는다. 보증금이 작은
 * 월세 계약은 반환보증 실익이 적어, 이 제도로 얼마가 보호되는지가 더 중요한 정보다.
 *
 * <p>판정 시점이 계약일이 아니라 경매개시결정 등기일 기준이라는 점도 함께 알린다.
 */
@Component
class SmallTenantProtectionRule implements PropertyRiskRule {

    private final FactRegistry facts;

    SmallTenantProtectionRule(FactRegistry facts) {
        this.facts = facts;
    }

    @Override
    public boolean appliesTo(PropertyFacts propertyFacts) {
        return propertyFacts.deposit() > 0;
    }

    @Override
    public Optional<CheckFinding> evaluate(PropertyFacts propertyFacts) {
        SmallTenantTier tier = SmallTenantTier.of(propertyFacts.regionCode());
        if (tier == null) {
            return Optional.of(finding(
                    CheckResult.UNKNOWN,
                    "지역 구간을 확인하지 못해 최우선변제 대상 여부를 판단할 수 없다.",
                    "경기도는 과밀억제권역 여부에 따라 기준이 달라진다. 시·군을 확인한다.",
                    null));
        }

        long cap = facts.won(tier.depositCapFact());
        long protectedAmount = facts.won(tier.protectedAmountFact());

        if (propertyFacts.deposit() > cap) {
            return Optional.of(finding(
                    CheckResult.WARN,
                    "보증금 %,d원이 %s 기준 상한 %,d원을 넘어 최우선변제 대상이 아니다.".formatted(propertyFacts.deposit(), tier.label(), cap),
                    "보증금 전액이 후순위가 된다. 반환보증 가입을 확인한다.",
                    tier.depositCapFact()));
        }

        long unprotected = Math.max(0, propertyFacts.deposit() - protectedAmount);
        String summary = "보증금 %,d원이 %s 기준 상한 %,d원 이하라 최대 %,d원까지 최우선변제 대상이다."
                .formatted(propertyFacts.deposit(), tier.label(), cap, protectedAmount);

        if (unprotected == 0) {
            return Optional.of(finding(
                    CheckResult.PASS,
                    summary + " 보증금 전액이 이 범위 안이다.",
                    "대항력을 갖춰야 적용된다. 잔금일에 전입신고를 마친다.",
                    tier.protectedAmountFact()));
        }
        return Optional.of(finding(
                CheckResult.WARN,
                summary + " 나머지 %,d원은 보호되지 않는다.".formatted(unprotected),
                "보호되지 않는 금액이 있다. 반환보증 가입을 확인한다. 판정 시점은 계약일이 아니라 경매개시결정 등기일 기준이다.",
                tier.protectedAmountFact()));
    }

    private CheckFinding finding(CheckResult result, String summary, String action, String factCode) {
        return new CheckFinding("SMALL_TENANT", "소액임차인 최우선변제", result, summary, action, factCode, null, false);
    }
}
