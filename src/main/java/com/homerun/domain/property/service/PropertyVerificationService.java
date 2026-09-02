package com.homerun.domain.property.service;

import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.CheckFinding;
import com.homerun.domain.property.dto.response.PropertyVerification;
import com.homerun.domain.property.type.CheckResult;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 매물 검증(PRP-01).
 *
 * <p>규칙을 모아 돌리고 결과를 묶는다. 항목을 더할 때는 {@link PropertyRiskRule} 구현을
 * 추가하기만 하면 되고 이 파일은 바뀌지 않는다.
 */
@Service
public class PropertyVerificationService {

    /** 나쁜 순서. 전체 판정을 고를 때 쓴다. */
    private static final List<CheckResult> SEVERITY =
            List.of(CheckResult.BLOCK, CheckResult.WARN, CheckResult.UNKNOWN, CheckResult.PASS);

    private final List<PropertyRiskRule> rules;

    public PropertyVerificationService(List<PropertyRiskRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public PropertyVerification verify(PropertyFacts facts) {
        List<CheckFinding> findings = rules.stream()
                .filter(rule -> rule.appliesTo(facts))
                .map(rule -> rule.evaluate(facts))
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparingInt(f -> SEVERITY.indexOf(f.result())))
                .toList();

        List<CheckFinding> blocking =
                findings.stream().filter(CheckFinding::blocking).toList();

        return new PropertyVerification(worst(findings), blocking, findings);
    }

    /**
     * 전체 판정.
     *
     * <p>확인 못 한 항목이 있어도 계약을 막지는 않는다. 다만 PASS 로 보이게 하지도 않는다.
     * 모르는 것을 안전하다고 말하면 사용자가 확인을 건너뛴다.
     */
    private CheckResult worst(List<CheckFinding> findings) {
        return findings.stream()
                .map(CheckFinding::result)
                .min(Comparator.comparingInt(SEVERITY::indexOf))
                .orElse(CheckResult.UNKNOWN);
    }
}
