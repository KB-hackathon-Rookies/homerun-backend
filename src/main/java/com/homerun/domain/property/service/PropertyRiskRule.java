package com.homerun.domain.property.service;

import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.CheckFinding;
import java.util.Optional;

/**
 * 매물 검증 규칙 하나.
 *
 * <p>규칙을 파일로 나눠 두면 항목을 더할 때 기존 파일을 열지 않아도 된다. 조회는 공통이고
 * 판정만 갈린다.
 */
public interface PropertyRiskRule {

    /** 이 규칙이 이 매물에 해당하는가. 전세만 보는 규칙 등이 있다. */
    boolean appliesTo(PropertyFacts facts);

    /** 확인할 자료가 없어 판정을 건너뛰면 비어 있다. */
    Optional<CheckFinding> evaluate(PropertyFacts facts);
}
