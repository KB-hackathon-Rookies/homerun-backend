package com.homerun.domain.property.dto.response;

import com.homerun.domain.property.type.CheckResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 매물 검증 결과 전체.
 *
 * @param overall 가장 나쁜 판정. BLOCK 이 하나라도 있으면 BLOCK 이다
 * @param blocking 계약을 막아야 하는 항목
 * @param findings 전체 항목
 */
@Schema(description = "매물 검증 결과")
public record PropertyVerification(CheckResult overall, List<CheckFinding> blocking, List<CheckFinding> findings) {

    public PropertyVerification {
        blocking = List.copyOf(blocking);
        findings = List.copyOf(findings);
    }

    /** 계약을 진행해도 되는가. 확인 못 한 항목이 남아 있어도 막지는 않는다. */
    public boolean contractable() {
        return blocking.isEmpty();
    }
}
