package com.homerun.domain.property.dto.response;

import com.homerun.domain.property.type.CheckResult;
import com.homerun.domain.property.type.TrafficLight;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 매물 검증 결과 전체.
 *
 * @param overall 가장 나쁜 판정. BLOCK 이 하나라도 있으면 BLOCK 이다
 * @param trafficLight 2루 매물 카드 상태(BR-10). {@code overall} 과 축이 다르다 — 반환보증
 *     126% 미달은 BLOCK 이지만 RED 가 아니다
 * @param trafficLightLabel 신호등의 한글 이름. 색만으로 구분하지 않는다(NFR-UX-03)
 * @param blocking 계약을 막아야 하는 항목
 * @param findings 전체 항목
 */
@Schema(description = "매물 검증 결과")
public record PropertyVerification(
        CheckResult overall,
        TrafficLight trafficLight,
        String trafficLightLabel,
        List<CheckFinding> blocking,
        List<CheckFinding> findings) {

    public PropertyVerification {
        blocking = List.copyOf(blocking);
        findings = List.copyOf(findings);
    }

    /** 신호등을 계산하지 않는 호출부(테스트 픽스처 등)를 위한 편의 생성자. */
    public PropertyVerification(CheckResult overall, List<CheckFinding> blocking, List<CheckFinding> findings) {
        this(overall, null, null, blocking, findings);
    }

    public PropertyVerification(
            CheckResult overall, TrafficLight trafficLight, List<CheckFinding> blocking, List<CheckFinding> findings) {
        this(overall, trafficLight, trafficLight == null ? null : trafficLight.label(), blocking, findings);
    }

    /** 계약을 진행해도 되는가. 확인 못 한 항목이 남아 있어도 막지는 않는다. */
    public boolean contractable() {
        return blocking.isEmpty();
    }
}
