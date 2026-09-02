package com.homerun.domain.property.service.rule;

import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.CheckFinding;
import com.homerun.domain.property.service.PropertyRiskRule;
import com.homerun.domain.property.type.CheckResult;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * PRP-01-06 다가구주택 제약 안내.
 *
 * <p>다가구는 전세자금대출이 안 되는 경우가 많다. 취급 은행이 제한되고 한도도 줄어든다
 * (FCT-122). 막는 게 아니라 미리 알려서 은행 사전상담을 받게 하는 항목이다.
 *
 * <p>전입세대확인서가 필요한데 이 서류는 온라인 발급이 안 된다. 주민센터를 가야 한다.
 */
@Component
class MultiHouseholdRule implements PropertyRiskRule {

    private static final String FACT = "FCT-122";

    @Override
    public boolean appliesTo(PropertyFacts facts) {
        return true;
    }

    @Override
    public Optional<CheckFinding> evaluate(PropertyFacts facts) {
        if (facts.multiHousehold() == null) {
            // 빈 결과로 버리면 항목이 목록에서 사라져 전체 판정이 PASS 로 보인다.
            // 확인하지 않은 것도 결과로 남겨야 누락이 드러난다.
            return Optional.of(new CheckFinding(
                    "MULTI_HOUSEHOLD",
                    "다가구주택 여부",
                    CheckResult.UNKNOWN,
                    "건축물대장에서 다가구주택 여부를 아직 확인하지 않았다.",
                    "건축물대장의 주택 유형을 확인한다. 다가구면 전세자금대출 취급 은행이 제한된다.",
                    FACT,
                    null,
                    false));
        }
        if (!facts.multiHousehold()) {
            return Optional.of(new CheckFinding(
                    "MULTI_HOUSEHOLD", "다가구주택 여부", CheckResult.PASS, "다가구주택이 아니다.", null, FACT, null, false));
        }
        return Optional.of(new CheckFinding(
                "MULTI_HOUSEHOLD",
                "다가구주택 여부",
                CheckResult.WARN,
                "다가구주택이다. 전세자금대출 취급 은행이 제한되고 한도가 줄어들 수 있다.",
                "계약 전에 은행 사전상담을 받는다. 전입세대확인서가 필요한데 주민센터 방문으로만 발급된다.",
                FACT,
                null,
                false));
    }
}
