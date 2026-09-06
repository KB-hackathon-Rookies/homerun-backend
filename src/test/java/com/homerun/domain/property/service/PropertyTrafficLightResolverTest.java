package com.homerun.domain.property.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.homerun.domain.property.type.CheckResult;
import com.homerun.domain.property.type.TrafficLight;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 신호등 파생 규칙(요구사항 명세서 BR-10). 저장 경로는 통합테스트가 본다. */
class PropertyTrafficLightResolverTest {

    private final PropertyTrafficLightResolver resolver = new PropertyTrafficLightResolver(
            mock(com.homerun.domain.property.repository.PropertyCheckRepository.class),
            mock(com.homerun.domain.property.repository.BankConsultationRepository.class));

    /** 등기부 3항목이 전부 통과한 상태. 여기서 하나씩 바꿔가며 전이를 본다. */
    private Map<String, CheckResult> registryAllPass() {
        Map<String, CheckResult> checks = new HashMap<>();
        checks.put("OWNER_MATCH", CheckResult.PASS);
        checks.put("TRUST_REGISTRATION", CheckResult.PASS);
        checks.put("REGISTRY_RESTRICTION", CheckResult.PASS);
        return checks;
    }

    private TrafficLight resolve(Map<String, CheckResult> checks, boolean consulted) {
        return resolver.resolve(checks::get, consulted);
    }

    @Test
    void should_beYellow_when_registryHasNotBeenCheckedYet() {
        // 등기부를 아직 안 뗀 매물. 항목이 통째로 비어 있다.
        assertThat(resolve(new HashMap<>(), false)).isEqualTo(TrafficLight.YELLOW);
    }

    @Test
    void should_beYellow_when_userAnsweredDontKnow() {
        // "모르겠어요"는 🔴도 🟢도 아니다(FR-P3-02).
        Map<String, CheckResult> checks = registryAllPass();
        checks.put("TRUST_REGISTRATION", CheckResult.UNKNOWN);

        assertThat(resolve(checks, false)).isEqualTo(TrafficLight.YELLOW);
    }

    @Test
    void should_beGreen_when_registryChecklistFullyPasses() {
        assertThat(resolve(registryAllPass(), false)).isEqualTo(TrafficLight.GREEN);
    }

    @Test
    void should_beBlue_when_bankConsultationRecorded() {
        assertThat(resolve(registryAllPass(), true)).isEqualTo(TrafficLight.BLUE);
    }

    @Test
    void should_beRed_when_anySpecifiedCauseBlocks() {
        for (String cause : new String[] {
            "VIOLATION_BUILDING", "NON_RESIDENTIAL", "TRUST_REGISTRATION", "REGISTRY_RESTRICTION", "OWNER_MATCH"
        }) {
            Map<String, CheckResult> checks = registryAllPass();
            checks.put(cause, CheckResult.BLOCK);

            assertThat(resolve(checks, false)).as(cause).isEqualTo(TrafficLight.RED);
        }
    }

    @Test
    void should_notBeRed_when_guaranteeCoverageBlocksButLoanIsUnaffected() {
        // 이 PR 의 핵심. OFFICIAL_PRICE 는 반환보증 126% 룰·담보인정비율에서 BLOCK 을 내지만
        // BR-11 이 "대출 판정과 독립적으로 계산·표시한다"고 했고 FR-P4-01 은 두 블록을 분리해
        // "대출 가능 = 안전한 집"으로 읽히지 않게 하라고 한다. 반환보증이 안 되는 집도 대출은 된다.
        Map<String, CheckResult> checks = registryAllPass();
        checks.put("OFFICIAL_PRICE", CheckResult.BLOCK);

        assertThat(resolve(checks, false)).isEqualTo(TrafficLight.GREEN);
    }

    @Test
    void should_notBeRed_when_onlyWarningsExist() {
        // 다가구·전세가율·체납은 경고지 RED 사유가 아니다(BR-10 ①: 다가구 → YELLOW + 경고 플래그).
        Map<String, CheckResult> checks = registryAllPass();
        checks.put("MULTI_HOUSEHOLD", CheckResult.WARN);
        checks.put("JEONSE_RATIO", CheckResult.WARN);
        checks.put("LANDLORD_TAX", CheckResult.WARN);

        assertThat(resolve(checks, false)).isEqualTo(TrafficLight.GREEN);
    }

    @Test
    void should_stayRed_evenAfterConsultation_becauseRedNeverShowsProducts() {
        // 상담 기록이 있어도 RED 가 BLUE 로 올라가지 않는다. RED 는 상품을 아예 노출하지 않는다(FR-P4-02).
        Map<String, CheckResult> checks = registryAllPass();
        checks.put("VIOLATION_BUILDING", CheckResult.BLOCK);

        assertThat(resolve(checks, true)).isEqualTo(TrafficLight.RED);
    }

    @Test
    void should_preferRedOverYellow_when_bothCausesExist() {
        Map<String, CheckResult> checks = registryAllPass();
        checks.put("OWNER_MATCH", CheckResult.UNKNOWN);
        checks.put("TRUST_REGISTRATION", CheckResult.BLOCK);

        assertThat(resolve(checks, false)).isEqualTo(TrafficLight.RED);
    }

    @Test
    void should_hideLoanProducts_onlyForRed() {
        assertThat(TrafficLight.RED.showsLoanProducts()).isFalse();
        assertThat(TrafficLight.YELLOW.showsLoanProducts()).isTrue();
        assertThat(TrafficLight.GREEN.showsLoanProducts()).isTrue();
        assertThat(TrafficLight.BLUE.showsLoanProducts()).isTrue();
    }
}
