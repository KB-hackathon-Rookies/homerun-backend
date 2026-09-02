package com.homerun.domain.property.service.rule;

import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.CheckFinding;
import com.homerun.domain.property.service.PropertyRiskRule;
import com.homerun.domain.property.type.CheckResult;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * PRP-01-02 건축물대장 확인 — 위반건축물.
 *
 * <p>위반건축물은 대출과 보증이 <b>둘 다</b> 거절된다(FCT-120). 근생빌라가 대표적이다.
 * 다른 조건이 아무리 좋아도 이 집으로는 진행할 수 없다.
 */
@Component
class ViolationBuildingRule implements PropertyRiskRule {

    private static final String FACT = "FCT-120";

    @Override
    public boolean appliesTo(PropertyFacts facts) {
        return true;
    }

    @Override
    public Optional<CheckFinding> evaluate(PropertyFacts facts) {
        if (facts.violationBuilding() == null) {
            return Optional.of(new CheckFinding(
                    "VIOLATION_BUILDING",
                    "위반건축물 여부",
                    CheckResult.UNKNOWN,
                    "건축물대장을 아직 확인하지 않았다.",
                    "건축물대장에서 위반건축물 표시를 확인한다. 표시가 있으면 대출과 보증이 모두 막힌다.",
                    FACT,
                    "https://www.gov.kr",
                    false));
        }
        if (!facts.violationBuilding()) {
            return Optional.of(new CheckFinding(
                    "VIOLATION_BUILDING",
                    "위반건축물 여부",
                    CheckResult.PASS,
                    "건축물대장에 위반건축물 표시가 없다.",
                    null,
                    FACT,
                    "https://www.gov.kr",
                    false));
        }
        return Optional.of(new CheckFinding(
                "VIOLATION_BUILDING",
                "위반건축물 여부",
                CheckResult.BLOCK,
                "위반건축물이다. 대출과 보증이 모두 거절된다.",
                "이 집으로는 정책자금과 반환보증을 쓸 수 없다. 다른 매물을 확인한다.",
                FACT,
                "https://www.gov.kr",
                false));
    }
}
