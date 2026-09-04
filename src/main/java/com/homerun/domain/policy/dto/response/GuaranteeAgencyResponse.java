package com.homerun.domain.policy.dto.response;

import com.homerun.domain.policy.entity.GuaranteeAgency;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 보증기관 비교(POL-03-08, GTE-01-03). 한도 산정 기준이 사람(HF)이냐 집(HUG)이냐로 갈리고,
 * SGI 는 자체 기준이라는 게 CLAUDE.md 도메인 지식의 핵심 — limitBasis 텍스트를 그대로 보여준다.
 */
public record GuaranteeAgencyResponse(
        String code,
        String name,
        String agencyType,
        String limitBasis,
        BigDecimal feeRateMin,
        BigDecimal feeRateMax,
        String note) {

    /** rule_json 에 없는 가입 제약 — HF 는 자사 전세대출 이용자만 반환보증 가입이 된다(GTE-01-03
     * 비고). 이후 기관이 늘면 여기 추가한다. */
    private static final Map<String, String> STATIC_NOTES = Map.of("HF", "자사(HF) 전세대출 이용자만 가입 가능. 대출 상환보증이지 반환보증은 아니다");

    public static GuaranteeAgencyResponse from(GuaranteeAgency agency) {
        return new GuaranteeAgencyResponse(
                agency.getCode(),
                agency.getName(),
                agency.getAgencyType(),
                agency.getLimitBasis(),
                agency.getFeeRateMin(),
                agency.getFeeRateMax(),
                STATIC_NOTES.get(agency.getCode()));
    }
}
