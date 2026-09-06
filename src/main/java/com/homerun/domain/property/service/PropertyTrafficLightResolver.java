package com.homerun.domain.property.service;

import com.homerun.domain.property.entity.PropertyCheck;
import com.homerun.domain.property.repository.BankConsultationRepository;
import com.homerun.domain.property.repository.PropertyCheckRepository;
import com.homerun.domain.property.type.CheckResult;
import com.homerun.domain.property.type.TrafficLight;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 매물 신호등을 검증 결과에서 파생한다(요구사항 명세서 BR-10).
 *
 * <p>따로 저장하지 않는다. 같은 상태를 검증 결과와 신호등 두 곳에 두면 반드시 갈라진다.
 *
 * <p><b>심각도(BLOCK)를 그대로 RED 로 옮기지 않는다.</b> 반환보증 126% 룰 미달과 담보인정비율
 * 초과({@code OFFICIAL_PRICE})는 BLOCK 이지만 RED 가 아니다 — BR-11 이 "대출 판정과 독립적으로
 * 계산·표시한다"고 했고, FR-P4-01 은 대출 블록과 보증금 안전 블록을 분리해 "대출 가능 = 안전한
 * 집"으로 읽히지 않게 하라고 한다. 반환보증이 안 되는 집도 대출은 될 수 있다.
 */
@Component
public class PropertyTrafficLightResolver {

    /** 명세서 3.6.1 이 RED 사유로 적은 것들. 여기 없는 BLOCK 은 신호등을 RED 로 만들지 않는다. */
    private static final Set<String> RED_CAUSES = Set.of(
            "VIOLATION_BUILDING", "NON_RESIDENTIAL", "TRUST_REGISTRATION", "REGISTRY_RESTRICTION", "OWNER_MATCH");

    /**
     * 등기부를 봐야 답할 수 있는 항목들(명세서 2-3 체크리스트).
     *
     * <p>근저당 채권최고액은 넣지 않는다. "과다" 임계값이 아직 미결(O-11)이라 확인했는지 여부를
     * 판단할 근거가 없다.
     */
    private static final Set<String> REQUIRED_CHECKS = Set.of(
            "VIOLATION_BUILDING", "NON_RESIDENTIAL", "OWNER_MATCH", "TRUST_REGISTRATION", "REGISTRY_RESTRICTION");

    private final PropertyCheckRepository checks;
    private final BankConsultationRepository consultations;

    public PropertyTrafficLightResolver(PropertyCheckRepository checks, BankConsultationRepository consultations) {
        this.checks = checks;
        this.consultations = consultations;
    }

    /**
     * 저장된 검증 결과와 상담 기록에서 신호등을 읽는다.
     *
     * <p>아직 검증하지 않은 매물은 등기부 항목이 통째로 비어 있어 YELLOW 다 — "아직 확인 안 함"이
     * 맞는 상태다.
     */
    public TrafficLight forProperty(Long planId, Long propertyId) {
        Map<String, CheckResult> byCode = checks.findByPropertyIdOrderById(propertyId).stream()
                .collect(Collectors.toMap(PropertyCheck::getCheckCode, PropertyCheck::getResult, (a, b) -> a));
        boolean consulted = !consultations
                .findAllByPlanIdAndPropertyIdOrderByConsultedAtDescIdDesc(planId, propertyId)
                .isEmpty();
        return resolve(byCode::get, consulted);
    }

    /**
     * @param checkResultOf 체크 코드를 주면 그 항목의 판정을 돌려주는 조회자. 그 항목을 아예 안
     *     봤으면 null 을 준다 — 등기부를 아직 안 뗀 경우가 여기 해당한다
     * @param consulted 이 매물로 은행 사전상담 결과가 입력됐는가
     */
    public TrafficLight resolve(Function<String, CheckResult> checkResultOf, boolean consulted) {
        if (RED_CAUSES.stream().anyMatch(blocked(checkResultOf))) {
            return TrafficLight.RED;
        }
        // 안 본 항목(null)과 "모르겠어요"(UNKNOWN)는 같은 뜻이다 — 둘 다 아직 확인이 필요하다.
        if (REQUIRED_CHECKS.stream().anyMatch(unconfirmed(checkResultOf))) {
            return TrafficLight.YELLOW;
        }
        return consulted ? TrafficLight.BLUE : TrafficLight.GREEN;
    }

    private Predicate<String> blocked(Function<String, CheckResult> checkResultOf) {
        return code -> checkResultOf.apply(code) == CheckResult.BLOCK;
    }

    private Predicate<String> unconfirmed(Function<String, CheckResult> checkResultOf) {
        return code -> {
            CheckResult result = checkResultOf.apply(code);
            return result == null || result == CheckResult.UNKNOWN;
        };
    }
}
