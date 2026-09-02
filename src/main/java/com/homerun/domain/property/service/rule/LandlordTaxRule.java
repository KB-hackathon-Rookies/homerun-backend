package com.homerun.domain.property.service.rule;

import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.CheckFinding;
import com.homerun.domain.property.service.PropertyRiskRule;
import com.homerun.domain.property.type.CheckResult;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * PRP-01-08 임대인 체납 확인.
 *
 * <p>조세채권은 임차인의 보증금보다 우선한다. 임대인이 세금을 체납하고 있으면 경매에서
 * 내 보증금보다 먼저 가져간다.
 *
 * <p>완납증명서는 계약 전에 받아야 한다. 계약 후에는 임대인이 협조하지 않을 수 있다.
 */
@Component
class LandlordTaxRule implements PropertyRiskRule {

    @Override
    public boolean appliesTo(PropertyFacts facts) {
        return facts.deposit() > 0;
    }

    @Override
    public Optional<CheckFinding> evaluate(PropertyFacts facts) {
        if (facts.landlordTaxUnpaid() == null) {
            return Optional.of(new CheckFinding(
                    "LANDLORD_TAX",
                    "임대인 체납",
                    CheckResult.UNKNOWN,
                    "임대인의 국세·지방세 완납 여부를 아직 확인하지 않았다.",
                    "계약 전에 국세·지방세 완납증명서를 요청한다. 계약 후에는 받기 어려울 수 있다.",
                    null,
                    null,
                    false));
        }
        if (!facts.landlordTaxUnpaid()) {
            return Optional.of(new CheckFinding(
                    "LANDLORD_TAX", "임대인 체납", CheckResult.PASS, "체납이 확인되지 않았다.", null, null, null, false));
        }
        return Optional.of(new CheckFinding(
                "LANDLORD_TAX",
                "임대인 체납",
                CheckResult.WARN,
                "임대인에게 체납이 있다. 조세채권이 보증금보다 우선 변제된다.",
                "체납액과 보증금 규모를 견줘 본다. 체납이 크면 계약하지 않는다.",
                null,
                null,
                false));
    }
}
