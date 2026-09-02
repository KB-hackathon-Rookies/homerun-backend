package com.homerun.domain.property.service.rule;

import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.CheckFinding;
import com.homerun.domain.property.service.PropertyRiskRule;
import com.homerun.domain.property.type.CheckResult;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** PRP-01-01 등기부 확인 — 소유자 일치. */
@Component
class OwnerMatchRule implements PropertyRiskRule {

    @Override
    public boolean appliesTo(PropertyFacts facts) {
        return true;
    }

    @Override
    public Optional<CheckFinding> evaluate(PropertyFacts facts) {
        if (facts.ownerMatches() == null) {
            return Optional.of(new CheckFinding(
                    "OWNER_MATCH",
                    "등기부상 소유자 일치",
                    CheckResult.UNKNOWN,
                    "등기사항전부증명서를 아직 확인하지 않았다.",
                    "인터넷등기소에서 등기사항전부증명서를 떼어 계약 상대방과 소유자가 같은지 확인한다.",
                    null,
                    "https://www.iros.go.kr",
                    false));
        }
        if (facts.ownerMatches()) {
            return Optional.of(new CheckFinding(
                    "OWNER_MATCH",
                    "등기부상 소유자 일치",
                    CheckResult.PASS,
                    "등기부상 소유자와 계약 상대방이 같다.",
                    null,
                    null,
                    "https://www.iros.go.kr",
                    false));
        }
        return Optional.of(new CheckFinding(
                "OWNER_MATCH",
                "등기부상 소유자 일치",
                CheckResult.BLOCK,
                "등기부상 소유자와 계약 상대방이 다르다.",
                "대리인 계약이면 위임장과 인감증명서를 확인한다. 확인되지 않으면 계약하지 않는다.",
                null,
                "https://www.iros.go.kr",
                false));
    }
}
