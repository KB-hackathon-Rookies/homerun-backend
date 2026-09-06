package com.homerun.domain.property.service.rule;

import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.CheckFinding;
import com.homerun.domain.property.service.PropertyRiskRule;
import com.homerun.domain.property.type.CheckResult;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 건축물대장 확인 — 근린생활시설(비주거).
 *
 * <p>근생은 주거용이 아니라서 전세대출도 반환보증도 안 된다(FCT-120, BR-09 "모든 상품 불가").
 * 겉보기엔 빌라와 똑같아서 사용자가 알아채기 어렵고, 계약한 뒤에는 되돌릴 수 없다 —
 * 명세서 3.6.1 이 RED 사유로 명시한 이유다.
 */
@Component
class NonResidentialRule implements PropertyRiskRule {

    private static final String FACT = "FCT-120";

    @Override
    public boolean appliesTo(PropertyFacts facts) {
        return true;
    }

    @Override
    public Optional<CheckFinding> evaluate(PropertyFacts facts) {
        if (facts.nonResidential() == null) {
            return Optional.of(new CheckFinding(
                    "NON_RESIDENTIAL",
                    "근린생활시설 여부",
                    CheckResult.UNKNOWN,
                    "건축물대장의 주용도를 아직 확인하지 않았다.",
                    "건축물대장 표제부의 주용도를 확인한다. 근린생활시설이면 대출과 보증이 모두 막힌다.",
                    FACT,
                    "https://www.gov.kr",
                    false));
        }
        if (!facts.nonResidential()) {
            return Optional.of(new CheckFinding(
                    "NON_RESIDENTIAL",
                    "근린생활시설 여부",
                    CheckResult.PASS,
                    "건축물대장 주용도가 주거용이다.",
                    null,
                    FACT,
                    "https://www.gov.kr",
                    false));
        }
        return Optional.of(new CheckFinding(
                "NON_RESIDENTIAL",
                "근린생활시설 여부",
                CheckResult.BLOCK,
                "근린생활시설이다. 주거용이 아니라 대출과 보증이 모두 거절된다.",
                "이 집으로는 어떤 전세 상품도 진행할 수 없다. 다른 매물을 확인한다.",
                FACT,
                "https://www.gov.kr",
                false));
    }
}
