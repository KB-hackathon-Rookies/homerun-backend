package com.homerun.domain.property.service;

import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.CheckFinding;
import com.homerun.domain.property.dto.response.PropertyVerification;
import com.homerun.domain.property.entity.PropertyCheck;
import com.homerun.domain.property.repository.PropertyCheckRepository;
import com.homerun.domain.property.type.CheckResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PropertyCheckRepository checks;
    private final Clock clock;

    public PropertyVerificationService(List<PropertyRiskRule> rules, PropertyCheckRepository checks, Clock clock) {
        this.rules = List.copyOf(rules);
        this.checks = checks;
        this.clock = clock;
    }

    /**
     * 검증하고 결과를 남긴다(PRP-01).
     *
     * <p>다시 검증하면 이전 기록을 지우고 새로 쓴다. 등기부를 다시 뗀 뒤에도 옛 판정이 남아
     * 있으면 어느 쪽이 지금 상태인지 알 수 없다.
     */
    @Transactional
    public PropertyVerification verifyAndRecord(Long propertyId, PropertyFacts facts) {
        PropertyVerification verification = verify(facts);
        Instant now = Instant.now(clock);

        checks.deleteByPropertyId(propertyId);
        checks.saveAll(verification.findings().stream()
                .map(finding -> new PropertyCheck(
                        propertyId,
                        finding.checkCode(),
                        finding.checkLabel(),
                        finding.result(),
                        finding.factCode(),
                        finding.sourceUrl(),
                        now))
                .toList());

        return verification;
    }

    /** 저장 없이 판정만 한다. 계약 전 화면처럼 매물을 아직 등록하지 않은 경우에 쓴다. */
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
