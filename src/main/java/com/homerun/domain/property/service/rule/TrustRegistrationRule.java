package com.homerun.domain.property.service.rule;

import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.CheckFinding;
import com.homerun.domain.property.service.PropertyRiskRule;
import com.homerun.domain.property.type.CheckResult;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * PRP-01-04 신탁등기 확인.
 *
 * <p>신탁등기가 있으면 소유권이 신탁사에 있다. 신탁사 동의 없이 등기부상 위탁자와 계약하면
 * 임차인 지위를 인정받지 못할 수 있다(FCT-121).
 */
@Component
class TrustRegistrationRule implements PropertyRiskRule {

    private static final String FACT = "FCT-121";

    @Override
    public boolean appliesTo(PropertyFacts facts) {
        return true;
    }

    @Override
    public Optional<CheckFinding> evaluate(PropertyFacts facts) {
        if (facts.trustRegistered() == null) {
            return Optional.of(new CheckFinding(
                    "TRUST_REGISTRATION",
                    "신탁등기 여부",
                    CheckResult.UNKNOWN,
                    "등기부에서 신탁등기 여부를 아직 확인하지 않았다.",
                    "등기사항전부증명서 갑구에 신탁등기가 있는지 확인한다.",
                    FACT,
                    "https://www.iros.go.kr",
                    false));
        }
        if (!facts.trustRegistered()) {
            return Optional.of(new CheckFinding(
                    "TRUST_REGISTRATION",
                    "신탁등기 여부",
                    CheckResult.PASS,
                    "신탁등기가 없다.",
                    null,
                    FACT,
                    "https://www.iros.go.kr",
                    false));
        }
        return Optional.of(new CheckFinding(
                "TRUST_REGISTRATION",
                "신탁등기 여부",
                CheckResult.BLOCK,
                "신탁등기가 있다. 신탁사 동의 없이 계약하면 임차인 지위를 인정받지 못할 수 있다.",
                "신탁원부를 확인하고 신탁사의 임대차 동의를 서면으로 받는다. 받지 못하면 계약하지 않는다.",
                FACT,
                "https://www.iros.go.kr",
                false));
    }
}
