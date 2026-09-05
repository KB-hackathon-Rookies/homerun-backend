package com.homerun.domain.property.service.rule;

import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.CheckFinding;
import com.homerun.domain.property.service.PropertyRiskRule;
import com.homerun.domain.property.type.CheckResult;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 등기부상 임차권등기·압류·가압류·가처분·경매 위험을 확인한다. */
@Component
class RegistryRestrictionRule implements PropertyRiskRule {

    @Override
    public boolean appliesTo(PropertyFacts facts) {
        return true;
    }

    @Override
    public Optional<CheckFinding> evaluate(PropertyFacts facts) {
        if (Boolean.TRUE.equals(facts.leaseholdRegistered())
                || Boolean.TRUE.equals(facts.seizureOrDispositionRestricted())
                || Boolean.TRUE.equals(facts.auctionInProgress())) {
            return Optional.of(new CheckFinding(
                    "REGISTRY_RESTRICTION",
                    "등기부 권리 제한",
                    CheckResult.BLOCK,
                    "임차권등기, 압류·가압류·가처분 또는 경매 기록이 있다.",
                    "계약을 진행하지 말고 권리관계 해소 여부를 등기부와 전문가를 통해 다시 확인한다.",
                    null,
                    "https://www.iros.go.kr",
                    false));
        }
        if (facts.leaseholdRegistered() == null
                || facts.seizureOrDispositionRestricted() == null
                || facts.auctionInProgress() == null) {
            return Optional.of(new CheckFinding(
                    "REGISTRY_RESTRICTION",
                    "등기부 권리 제한",
                    CheckResult.UNKNOWN,
                    "등기부의 임차권등기·압류·가압류·가처분·경매 여부를 모두 확인하지 않았다.",
                    "최신 등기사항전부증명서의 갑구와 을구를 확인한다.",
                    null,
                    "https://www.iros.go.kr",
                    false));
        }
        return Optional.of(new CheckFinding(
                "REGISTRY_RESTRICTION",
                "등기부 권리 제한",
                CheckResult.PASS,
                "확인한 등기부에 임차권등기·권리 제한·경매 기록이 없다.",
                null,
                null,
                "https://www.iros.go.kr",
                false));
    }
}
